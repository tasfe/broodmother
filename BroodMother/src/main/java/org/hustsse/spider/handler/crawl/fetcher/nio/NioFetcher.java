package org.hustsse.spider.handler.crawl.fetcher.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.hustsse.spider.Spider;
import org.hustsse.spider.exception.BossException;
import org.hustsse.spider.framework.Handler;
import org.hustsse.spider.framework.HandlerContext;
import org.hustsse.spider.handler.crawl.fetcher.httpcodec.DefaultHttpRequest;
import org.hustsse.spider.handler.crawl.fetcher.httpcodec.DefaultHttpResponse;
import org.hustsse.spider.handler.crawl.fetcher.httpcodec.HttpCodecUtil;
import org.hustsse.spider.handler.crawl.fetcher.httpcodec.HttpHeaders;
import org.hustsse.spider.handler.crawl.fetcher.httpcodec.HttpMethod;
import org.hustsse.spider.handler.crawl.fetcher.httpcodec.HttpRequest;
import org.hustsse.spider.handler.crawl.fetcher.httpcodec.HttpResponse;
import org.hustsse.spider.handler.crawl.fetcher.httpcodec.HttpResponseStatus;
import org.hustsse.spider.handler.crawl.fetcher.httpcodec.HttpVersion;
import org.hustsse.spider.model.CrawlURL;
import org.hustsse.spider.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NioFetcher implements Handler {
	private static final Logger logger = LoggerFactory.getLogger(NioFetcher.class);

	static final int DEFAULT_REACTOR_NUMS = Runtime.getRuntime().availableProcessors();
	static final int DEFAULT_BOSS_NUMS = 1;
	private Reactor[] reactors;
	private Boss[] boss;
	private int curReactorIndex;
	private int curBossIndex;

	// 对http响应做摘要的算法

	public NioFetcher(Executor bossExecutor, Executor reactorExecutor) {
		this(bossExecutor, reactorExecutor, DEFAULT_BOSS_NUMS, DEFAULT_REACTOR_NUMS);
	}

	public NioFetcher(Executor bossExecutor, Executor reactorExecutor, int reactorCount) {
		this(bossExecutor, reactorExecutor, DEFAULT_BOSS_NUMS, reactorCount);
	}

	// 快捷方式，使用的线程池都是 newCached 的
	public NioFetcher(int reactorCount) {
		this(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), DEFAULT_BOSS_NUMS, reactorCount);
	}

	public NioFetcher() {
		this(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), DEFAULT_BOSS_NUMS, DEFAULT_REACTOR_NUMS);
	}

	// bossCount暂时不开放，默认为1
	private NioFetcher(Executor bossExecutor, Executor reactorExecutor, int bossCount, int reactorCount) {
		reactors = new Reactor[reactorCount];
		boss = new Boss[bossCount];
		for (int i = 0; i < reactorCount; i++) {
			reactors[i] = new Reactor(reactorExecutor, i);
		}
		for (int i = 0; i < bossCount; i++) {
			boss[i] = new Boss(bossExecutor, i);
		}
	}

	@Override
	public void process(HandlerContext ctx, CrawlURL url) {
		CrawlURL uriProcessed = (CrawlURL) url;
		Object msg = url.getPipeline().getMessage();
		// 初始
		if (msg == null) {
			try {
				SocketChannel channel = SocketChannel.open();
				channel.configureBlocking(false);
				SocketAddress reomteAddress = new InetSocketAddress(uriProcessed.getURL().getHost(), uriProcessed.getURL().getPort());

				// 立即connect，成功则注册到Reactor
				if (channel.connect(reomteAddress)) {
					nextReactor().register(channel, uriProcessed);
					// 连接成功后立刻发送http请求。考虑到Http请求一般不会太大（GET），目前的处理方式是一旦连接上立刻发送并假设一定可以发送成功
					sendHttpRequest(channel, uriProcessed);
				} else {
					// 失败则注册到Boss，监听其OP_CONNECT状态
					long curNano = System.nanoTime();
					uriProcessed.setProcessorAttr(NioConstants._CONNECT_DEADLINE_NANOS, curNano
							+ NioConstants.DEFAULT_CONNECT_TIMEOUT_MILLIS * 1000 * 1000L);// 1ms=
																							// 1000*1000ns
					nextBoss().register(channel, uriProcessed);
				}

				ctx.pause();
			} catch (IOException e) {
				// TODO 发送http请求失败了，重试？
				e.printStackTrace();
			}
		} else {// 读到了数据
			/*
			 * nio方式每次读取一个片段,Composite ByteBuffer在这里就
			 * 发挥作用了,它能将这些小片的ByteBuffer组合起来,就像一块 连续的ByteBuffer使用它。
			 *
			 * 稍次的处理方式是用一个可以动态扩容的ByteBuffer，会发生 内存拷贝。
			 *
			 * 我们没有Netty CompositeChannelBuffer那样的现成制品，
			 * 采用的方式是：首先使用一个List<ByteBuffer>存储直到接收 完毕，为了解析方便再将其复制到一个连续ByteBuffer
			 */
			ByteBuffer segment = (ByteBuffer) msg;
			List<ByteBuffer> rawResponse = appendToSegList(segment, uriProcessed);
			url.getPipeline().clearMessage();

			// 读取完毕或失败，继续在pipeline中流转，否则暂停，等待下一片段的到来
			if ((Integer) url.getProcessorAttr(NioConstants._FETCH_STATUS) >= NioConstants.FETCH_FINISHED) {
				ByteBuffer merged = merge(rawResponse);
				merged.flip(); // *** 设置为写出模式 ***

				// raw response & http response are backed by the SAME buffer,
				// be careful to modify them
				uriProcessed.setProcessorAttr(NioConstants._RAW_RESPONSE, merged); // 后续processor会用到原始http响应么？不需要的话可以删除之
				HttpResponse response = decodeHttpResponse(merged.duplicate());
				uriProcessed.setResponse(response);

				// TODO 对content生成消息摘要，用于对内容判重
				ctx.proceed();
			} else {
				ctx.pause();
			}
		}
	}

	/**
	 * 将读到的Http响应片段添加到URI对应的segment list末尾
	 *
	 * @param segment
	 * @param uriProcessed
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private List<ByteBuffer> appendToSegList(ByteBuffer segment, CrawlURL uriProcessed) {
		Object val = uriProcessed.getProcessorAttr(NioConstants._RAW_RESPONSE);
		List<ByteBuffer> rawResponse;
		if (val == null) {
			rawResponse = new LinkedList<ByteBuffer>();
		} else {
			rawResponse = (List<ByteBuffer>) val;
		}
		rawResponse.add(segment);
		uriProcessed.setProcessorAttr(NioConstants._RAW_RESPONSE, rawResponse);
		return rawResponse;
	}

	protected static enum State {
		EMPTY_CONTENT, READ_VARIABLE_LENGTH_CONTENT, READ_FIXED_LENGTH_CONTENT,
	}

	/**
	 * 解码http响应。
	 *
	 * <p>
	 * 如果响应没有正文，返回的结果{@link HttpResponse#getContent()}将返回null。
	 * 否则将返回一个已经flip为写出模式的ByteBuffer对象。
	 *
	 * @param merged
	 * @return
	 */
	private HttpResponse decodeHttpResponse(ByteBuffer merged) {
		// response 第一行
		String[] splitedInitialLine = splitInitialLine(readLine(merged));
		HttpResponse r = new DefaultHttpResponse(HttpVersion.valueOf(splitedInitialLine[0]), new HttpResponseStatus(
				Integer.valueOf(splitedInitialLine[1]), splitedInitialLine[2]));
		// headers
		State nextStep = readHeaders(r, merged);
		// contents
		switch (nextStep) {
		case EMPTY_CONTENT:
			// No content is expected.
			// Remove the headers which are not supposed to be present not
			// to confuse subsequent handlers.
			r.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
			break;
		case READ_FIXED_LENGTH_CONTENT:
			// we have a content-length so we just read the correct number of
			// bytes
			readFixedLengthContent(merged, r);
			break;
		case READ_VARIABLE_LENGTH_CONTENT:
			readVariableLengthContent(merged, r);
			break;
		default:
		}
		return r;
	}

	private void readVariableLengthContent(ByteBuffer merged, HttpResponse r) {
		ByteBuffer content = merged.slice();
		r.setContent(content);
	}

	private void readFixedLengthContent(ByteBuffer buffer, HttpResponse message) {
		long length = HttpHeaders.getContentLength(message, -1);
		assert length <= Integer.MAX_VALUE;

		// 为了防止拷贝，response的content与merged底层使用同一个数据结构，merged
		// = 响应头 + headers
		// +正文，前两者的内容不会太多，
		buffer.limit(buffer.position() + (int) length);
		ByteBuffer content = buffer.slice();
		message.setContent(content);
	}

	/**
	 * 根据响应的status code判断是否有content
	 *
	 * @param msg
	 * @return
	 */
	protected boolean isContentAlwaysEmpty(HttpResponse msg) {
		HttpResponse res = (HttpResponse) msg;
		int code = res.getStatus().getCode();
		if (code < 200) {
			return true;
		}
		switch (code) {
		case 204:
		case 205:
		case 304:
			return true;
		}
		return false;
	}

	private String readLine(ByteBuffer buffer) {
		StringBuilder sb = new StringBuilder(64);
		while (true) {
			byte nextByte = buffer.get();
			if (nextByte == HttpCodecUtil.CR) {
				nextByte = buffer.get();
				if (nextByte == HttpCodecUtil.LF) {
					return sb.toString();
				}
			} else if (nextByte == HttpCodecUtil.LF) {
				return sb.toString();
			} else {
				sb.append((char) nextByte);
			}
		}
	}

	private String[] splitInitialLine(String sb) {
		int aStart;
		int aEnd;
		int bStart;
		int bEnd;
		int cStart;
		int cEnd;

		aStart = findNonWhitespace(sb, 0);
		aEnd = findWhitespace(sb, aStart);

		bStart = findNonWhitespace(sb, aEnd);
		bEnd = findWhitespace(sb, bStart);

		cStart = findNonWhitespace(sb, bEnd);
		cEnd = findEndOfString(sb);

		return new String[] { sb.substring(aStart, aEnd), sb.substring(bStart, bEnd), cStart < cEnd ? sb.substring(cStart, cEnd) : "" };
	}

	private int findNonWhitespace(String sb, int offset) {
		int result;
		for (result = offset; result < sb.length(); result++) {
			if (!Character.isWhitespace(sb.charAt(result))) {
				break;
			}
		}
		return result;
	}

	private int findWhitespace(String sb, int offset) {
		int result;
		for (result = offset; result < sb.length(); result++) {
			if (Character.isWhitespace(sb.charAt(result))) {
				break;
			}
		}
		return result;
	}

	private int findEndOfString(String sb) {
		int result;
		for (result = sb.length(); result > 0; result--) {
			if (!Character.isWhitespace(sb.charAt(result - 1))) {
				break;
			}
		}
		return result;
	}

	private State readHeaders(HttpResponse message, ByteBuffer buffer) {
		String line = readHeader(buffer);
		String name = null;
		String value = null;
		if (line.length() != 0) {
			message.clearHeaders();
			do {
				char firstChar = line.charAt(0);
				if (name != null && (firstChar == ' ' || firstChar == '\t')) {
					value = value + ' ' + line.trim();
				} else {
					if (name != null) {
						message.addHeader(name, value);
					}
					String[] header = splitHeader(line);
					name = header[0];
					value = header[1];
				}

				line = readHeader(buffer);
			} while (line.length() != 0);

			// Add the last header.
			if (name != null) {
				message.addHeader(name, value);
			}
		}

		State nextStep;
		// 判断状态，决定下一步要怎么解析

		// 采用的协议是http1.0，因此不考虑chunked情况
		if (isContentAlwaysEmpty(message)) {
			nextStep = State.EMPTY_CONTENT;
		} else if (HttpHeaders.getContentLength(message, -1) >= 0) {
			nextStep = State.READ_FIXED_LENGTH_CONTENT;
		} else {
			nextStep = State.READ_VARIABLE_LENGTH_CONTENT;
		}
		return nextStep;
	}

	private String readHeader(ByteBuffer buffer) {
		StringBuilder sb = new StringBuilder(64);
		loop: for (;;) {
			char nextByte = (char) buffer.get();

			switch (nextByte) {
			case HttpCodecUtil.CR:
				nextByte = (char) buffer.get();
				if (nextByte == HttpCodecUtil.LF) {
					break loop;
				}
				break;
			case HttpCodecUtil.LF:
				break loop;
			}
			sb.append(nextByte);
		}

		return sb.toString();
	}

	private String[] splitHeader(String sb) {
		final int length = sb.length();
		int nameStart;
		int nameEnd;
		int colonEnd;
		int valueStart;
		int valueEnd;

		nameStart = findNonWhitespace(sb, 0);
		for (nameEnd = nameStart; nameEnd < length; nameEnd++) {
			char ch = sb.charAt(nameEnd);
			if (ch == ':' || Character.isWhitespace(ch)) {
				break;
			}
		}

		for (colonEnd = nameEnd; colonEnd < length; colonEnd++) {
			if (sb.charAt(colonEnd) == ':') {
				colonEnd++;
				break;
			}
		}

		valueStart = findNonWhitespace(sb, colonEnd);
		if (valueStart == length) {
			return new String[] { sb.substring(nameStart, nameEnd), "" };
		}

		valueEnd = findEndOfString(sb);
		return new String[] { sb.substring(nameStart, nameEnd), sb.substring(valueStart, valueEnd) };
	}

	private ByteBuffer merge(List<ByteBuffer> buffers) {
		int size = 0;
		for (ByteBuffer buffer : buffers) {
			size += buffer.position();
		}
		ByteBuffer merged = ByteBuffer.allocate(size);
		for (ByteBuffer buffer : buffers) {
			buffer.flip();
			merged.put(buffer);
		}
		return merged;
	}

	private void sendHttpRequest(SocketChannel channel, CrawlURL url) throws IOException {
		String host = url.getURL().getEscapedHost();
		String path = url.getURL().getEscapedPath();
		String query = url.getURL().getEscapedQuery();
		if (query != null) {
			path += '?';
			path += query;
		}
		// 降级到1.0协议，避免对Chunked解码。毕竟我们在拿到所有数据之后才能进行下一步处理
		HttpRequest httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, path);
		httpRequest.setHeader(HttpHeaders.Names.HOST, host);
		httpRequest.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
		httpRequest.setHeader(HttpHeaders.Names.USER_AGENT, Spider.DEFAULT_USER_AGENT);
		httpRequest.setHeader(HttpHeaders.Names.ACCEPT, "text/*");

		ByteBuffer reqBuffer = encodeHttpRequest(httpRequest);
		channel.write(reqBuffer); // http 1.1的长连接能发多个请求吗？
	}

	/**
	 * 编码Http请求。
	 *
	 * <p>
	 * 注意HTTP message是个文本协议，除了正文的字符集是在Header中指定，其他都是用US-ASCII字符集进行传输的，
	 * 注意区分charset和encoding的区别。
	 * <p>
	 * Http message中的“CRLF”，是ASCII字符集中的两个字符，分别对应“Carriage Return(回车)”和“Line Feed
	 * (换行)”，这二者的含义不同，具体见{@link HttpCodecUtil#CR}和{@link HttpCodecUtil#LF}
	 * <p>
	 * 各个平台上显示或者保存文本时的line seperator用什么字符是平台相关的，比如Unix系统里，每行结尾只有“换行”，即“\n”；
	 * Windows系统里，每行结尾是“回车换行”，即“\r\n”；Mac系统里，每行结尾是“回车”，即"\r"。 <br>
	 * 可以用如下方式得到平台相关的line seperator:
	 *
	 * <pre>
	 * <code>
	 *   newLine = new Formatter().format("%n").toString()
	 * </code>
	 * </pre>
	 *
	 * <h2>URL的encoding</h2>
	 * <p>
	 * URL最终作为Http请求行被发送时，由于发送时采用ASCII字符集，如何处理如中文这种字符？答案是使用一个ASCII的超集对其
	 * 进行“百分号Encoding”，标准推荐使用UTF-8。当用户在浏览器中输入url时，会使用浏览器默认字符集对其encoding。
	 *
	 * @param request
	 * @return
	 * @see StringUtil#NEWLINE
	 * @throws CharacterCodingException
	 */
	private ByteBuffer encodeHttpRequest(HttpRequest request) throws CharacterCodingException {
		Charset charset = Charset.forName("ASCII"); // HTTP协议使用ASCII字符集进行传输
		CharsetEncoder encoder = charset.newEncoder();
		String requestStr = request.toRequestStr();
		return encoder.encode(CharBuffer.wrap(requestStr));
	}

	public Boss nextBoss() {
		return boss[curBossIndex++ % boss.length];
	}

	public Reactor nextReactor() {
		return reactors[curReactorIndex++ % reactors.length];
	}

	/**
	 * Boss，监听未能一次性连接成功的CHannel的OP_CONNECT，在成功连接后移交给Reactor， 同时负责超时控制。
	 *
	 * @author novo
	 *
	 */
	class Boss implements Runnable {
		private Executor bossExecutor;
		private int index;
		private Queue<Runnable> registerQueue = new LinkedBlockingQueue<Runnable>();
		private Selector selector;
		private boolean started;
		private String threadName;
		private long lastConnectTimeoutCheckTimeNanos;

		public Boss(Executor bossExecutor, int i) {
			this.bossExecutor = bossExecutor;
			this.index = i;
			threadName = "New I/O boss线程 #" + index;
		}

		@Override
		public void run() {
			Thread.currentThread().setName(threadName);
			while (started) {
				try {
					int selectedKeyCount = selector.select(500);
					processRegisterTaskQueue();
					if (selectedKeyCount > 0) {
						processSelectedKeys(selector.selectedKeys());
					}

					// 超时处理
					long currentTimeNanos = System.nanoTime();
					if (currentTimeNanos - lastConnectTimeoutCheckTimeNanos >= 500 * 1000000L) {
						lastConnectTimeoutCheckTimeNanos = currentTimeNanos;
						processConnectTimeout(selector.keys(), currentTimeNanos);
					}

					// TODO 关闭

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		/**
		 * 处理超时连接
		 *
		 * @param keys
		 * @param currentTimeNanos
		 */
		private void processConnectTimeout(Set<SelectionKey> keys, long currentTimeNanos) {
			for (Iterator<SelectionKey> i = keys.iterator(); i.hasNext();) {
				SelectionKey k = i.next();
				CrawlURL u = (CrawlURL) k.attachment();
				Long connectDeadlineNanos = (Long) u.getProcessorAttr(NioConstants._CONNECT_DEADLINE_NANOS);
				if (connectDeadlineNanos > 0 && currentTimeNanos > connectDeadlineNanos) {
					System.out.println("uri" + u + "连接失败！");
					// cancel the key and close the channel
					k.cancel();
					try {
						k.channel().close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}

		/**
		 * Boss处理Select出来的key，注册到Boss的Channel只会注册OP_CONNECT， 在该方法内部调用
		 * {@link SocketChannel#finishConnect()}完成连接的剩余
		 * 步骤，如果成功连接，则将该Channel从Boss移入Reactor；否则取消注册，关闭 Channel，（并做些日志记录？）
		 *
		 * @param selectedKeys
		 */
		private void processSelectedKeys(Set<SelectionKey> selectedKeys) {
			for (Iterator<SelectionKey> i = selectedKeys.iterator(); i.hasNext();) {
				SelectionKey key = i.next();
				i.remove();
				SocketChannel channel = (SocketChannel) key.channel();
				CrawlURL url = (CrawlURL) key.attachment();
				if (key.isConnectable()) {
					try {
						if (channel.finishConnect()) {
							key.cancel();
							nextReactor().register(channel, (CrawlURL) key.attachment());
							// TODO 这里有个问题，因为register是异步的，有可能稍后才会真正地注册到一个selector上，但是在那之前已经发送请求了。
							// 不过对结果没影响，在注册到selector前如果有数据到了，最后还是会select出来。
							// 移交给Reactor后立刻发送http请求，考虑到Http请求一般不会太大（GET），目前的处理方式是一旦连接上立刻发送并假设一定可以发送成功
							sendHttpRequest(channel, url);
						}
					} catch (IOException e) {
						// TODO 重试？
						logger.error("failed connecting server，url:\n" + url, e);
						key.cancel();
						e.printStackTrace();
						try {
							channel.close();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
				}
			}
		}

		private void processRegisterTaskQueue() {
			while (true) {
				Runnable task = registerQueue.poll();
				if (task == null)
					break;
				task.run();
			}
		}

		/**
		 * 注册一个Channel到Boss上，监听OP_CONNECT状态。如果Boss未启动，则 提交到BossExecutor中执行。
		 *
		 * @param channel
		 * @param uri
		 */
		void register(SocketChannel channel, CrawlURL uri) {
			if (!started) {
				// Open a selector if this boss didn't start yet.
				try {
					this.selector = Selector.open();
				} catch (Throwable t) {
					throw new BossException("Failed to create a selector.", t);
				}
				bossExecutor.execute(this);
				started = true;
			}
			assert started;
			RegisterTask task = new RegisterTask(channel, this, uri);
			registerQueue.offer(task);
		}

	}

	/**
	 * Register Task for Boss
	 *
	 * @author Administrator
	 *
	 */
	private static class RegisterTask implements Runnable {
		SocketChannel channel;
		Boss boss;
		CrawlURL uri;

		public RegisterTask(SocketChannel channel, Boss boss, CrawlURL uri) {
			this.channel = channel;
			this.boss = boss;
			this.uri = uri;
		}

		@Override
		public void run() {
			try {
				channel.register(boss.selector, SelectionKey.OP_CONNECT, uri);
			} catch (ClosedChannelException e) {
				e.printStackTrace();
			}
		}
	}
}
