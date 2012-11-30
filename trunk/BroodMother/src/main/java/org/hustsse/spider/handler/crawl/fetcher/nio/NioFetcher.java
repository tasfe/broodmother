package org.hustsse.spider.handler.crawl.fetcher.nio;

import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants.*;
import static org.hustsse.spider.model.CrawlURL.*;

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

import org.apache.commons.lang3.StringUtils;
import org.hustsse.spider.exception.BossException;
import org.hustsse.spider.framework.Handler;
import org.hustsse.spider.framework.HandlerContext;
import org.hustsse.spider.framework.Pipeline;
import org.hustsse.spider.handler.AbstractBeanNameAwareHandler;
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
import org.hustsse.spider.util.CommonUtils;
import org.hustsse.spider.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NioFetcher  extends AbstractBeanNameAwareHandler {
	private static final Logger logger = LoggerFactory.getLogger(NioFetcher.class);

	static final int DEFAULT_REACTOR_NUMS = Runtime.getRuntime().availableProcessors() * 2;
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
		Object msg = url.getPipeline().getMessage();
		// 初始
		if (msg == null) {
			try {
				SocketChannel channel = SocketChannel.open();
				channel.configureBlocking(false);

				SocketAddress reomteAddress = new InetSocketAddress(url.getDns().getIp(), url.getURL().getPort());
				// 立即connect，成功则注册到Reactor
				if (channel.connect(reomteAddress)) {
					/*
					 * 低速网络环境下（10K），在Reactor#processReableKey调用channel.read方法可能抛异常
					 * ：java.io.IOException: 远程主机强迫关闭了一个现有的连接。 第一个异常抛出时连接数为60+，
					 * 猜测是连接上之后过长时间没有活动，被server断掉了。
					 *
					 * 在连接上时设置connect time，在抛出异常时查看过去的时间。
					 */
					url.setHandlerAttr(_CONNECT_SUCCESS_MILLIS, System.currentTimeMillis());
					// 连接成功后立刻发送http请求。考虑到Http请求一般不会太大（GET），目前的处理方式是一旦连接上立刻发送并假设一定可以发送成功
					sendHttpRequest(channel, url);
					ctx.pause();
					nextReactor().register(channel, url);
				} else {
					url.setHandlerAttr(_CONNECT_ATTEMPT_MILLIS, System.currentTimeMillis());
					// 失败则注册到Boss，监听其OP_CONNECT状态
					long curNano = System.nanoTime();
					url.setHandlerAttr(_CONNECT_DEADLINE_NANOS, curNano + DEFAULT_CONNECT_TIMEOUT_MILLIS * 1000 * 1000L);
					// pause before register，boss/reactor线程可能在pause之前resume
					ctx.pause();
					nextBoss().register(channel, url);
				}

			} catch (IOException e) {
				logger.debug("发送http请求失败，url：{},重试次数：{}", new Object[] { url, url.getRetryTimes(), e });
				url.setFetchStatus(FETCH_FAILED);
				ctx.finish();
			}

			return;
		}

		// resumed
		int fetchStatus = url.getFetchStatus();
		switch (fetchStatus) {
		case FETCH_FAILED:
			url.setNeedRetry(true);
			ctx.finish();
			return;
		case FETCH_ING:
			appendToSegList((ByteBuffer) msg, url);
			url.getPipeline().clearMessage();
			ctx.pause();
			return;
		case FETCH_SUCCESSED:
			ByteBuffer segment = (ByteBuffer) msg;
			List<ByteBuffer> responseSegments = appendToSegList(segment, url);
			ByteBuffer merged = merge(responseSegments);
			merged.flip(); // *** 设置为写出模式 ***

			// raw http response(ByteBuffer形式，未解析)和解析之后的HttpResponse（但是Content依然是ByteBuffer形式），
			// are backed by the SAME buffer,be careful to modify them
			url.setHandlerAttr(_RAW_RESPONSE, merged); // 后续processor会用到原始http响应么？不需要的话可以删除之
			CommonUtils.toFile(merged.duplicate(), "R:\\www_topit_me.html", url);
			HttpResponse response = decodeHttpResponse(merged.duplicate());
			url.setResponse(response);

			// TODO 对content生成消息摘要，用于对内容判重
			ctx.proceed();
			return;
		default:
			return;
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
		Object val = uriProcessed.getProcessorAttr(_RAW_RESPONSE);
		List<ByteBuffer> responseSegments;
		if (val == null) {
			responseSegments = new LinkedList<ByteBuffer>();
		} else {
			responseSegments = (List<ByteBuffer>) val;
		}
		responseSegments.add(segment);
		uriProcessed.setHandlerAttr(_RAW_RESPONSE, responseSegments);
		return responseSegments;
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
		String firstline = readLine(merged);
		String[] splitedInitialLine = splitInitialLine(firstline);
		if (!firstline.toLowerCase().startsWith("http"))
			System.out.println("=============" + firstline);
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
		int contentEndPostion = buffer.position() + (int) length;
		// 读取完毕，但内容不全 TODO 这么处理合适么？
		if(contentEndPostion > buffer.capacity()) {
			readVariableLengthContent(buffer, message);
			return;
		}

		// 为了防止拷贝，response的content与merged底层使用同一个数据结构，merged
		// = 响应头 + headers
		// +正文，前两者的内容不会太多，
		buffer.limit(contentEndPostion);
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

	/**
	 * 组装http请求并发送
	 *
	 * @param channel
	 * @param url
	 * @throws IOException
	 */
	private void sendHttpRequest(SocketChannel channel, CrawlURL url) throws IOException {
		String host = url.getURL().getEscapedHost();
		String path = url.getURL().getEscapedPath();
		String query = url.getURL().getEscapedQuery();
		if (StringUtils.isEmpty(path))
			path = "/";
		if (query != null) {
			path += '?';
			path += query;
		}
		// 降级到1.0协议，避免对Chunked解码。毕竟我们在拿到所有数据之后才能进行下一步处理
		HttpRequest httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, path);
		httpRequest.setHeader(HttpHeaders.Names.HOST, host);
		httpRequest.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
		// httpRequest.setHeader(HttpHeaders.Names.USER_AGENT,
		// Spider.DEFAULT_USER_AGENT);
		httpRequest.setHeader(HttpHeaders.Names.USER_AGENT, "Mozilla/5.0 (Windows NT 6.1; rv:16.0) Gecko/20100101 Firefox/16.0");
		httpRequest.setHeader(HttpHeaders.Names.ACCEPT, "text/*");

		ByteBuffer reqBuffer = encodeHttpRequest(httpRequest);
		// 一些统计信息
		url.setHandlerAttr(_REQUEST_SIZE, reqBuffer.capacity());
		url.setHandlerAttr(_REQUEST_ALREADY_SEND_SIZE, 0);
		url.setHandlerAttr(_REQUEST_SEND_TIMES, 0);
		int writtenBytes = 0;
		for (int i = WRITE_SPIN_COUNT; i > 0; i--) {
			writtenBytes = channel.write(reqBuffer);
			if (writtenBytes != 0) {
				url.setHandlerAttr(_LAST_SEND_REQUEST_MILLIS, System.currentTimeMillis());
				url.setHandlerAttr(_REQUEST_ALREADY_SEND_SIZE, writtenBytes);
				url.setHandlerAttr(_REQUEST_SEND_TIMES, (Integer) url.getProcessorAttr(_REQUEST_SEND_TIMES) + 1);
				break;
			}
		}
		// 99%的情况都会一次性发送完毕，不会注册到reactor上，但是以防万一还是做点处理。
		boolean reqSendFinished = !reqBuffer.hasRemaining();
		url.setHandlerAttr(_REQUEST_SEND_FINISHED, reqSendFinished);
		if (!reqSendFinished) {
			url.setHandlerAttr(_REQUEST_BUFFER, reqBuffer); // save the
																// request
																// buffer for
																// next sending
		}
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

		/**
		 * fail the url fetch, cancel the key registion and close the channel,
		 * and resume the pipeline.
		 *
		 * @param k
		 */
		private void failAndResumePipeline(SelectionKey k) {
			CrawlURL url = (CrawlURL) k.attachment();
			url.setFetchStatus(FETCH_FAILED);
			k.cancel();
			try {
				k.channel().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			url.getPipeline().resume(Pipeline.EMPTY_MSG);
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
				Long connectDeadlineNanos = (Long) u.getProcessorAttr(_CONNECT_DEADLINE_NANOS);
				if (connectDeadlineNanos > 0 && currentTimeNanos > connectDeadlineNanos) {
					int duration = getConAttemptDuration(u);
					logger.debug("连接服务器超时，距尝试连接时刻(s)：{},url：{},重试次数：{}", new Object[] { duration, u, u.getRetryTimes() });
					// cancel the key and close the channel
					failAndResumePipeline(k);
				}
			}
		}

		private int getConAttemptDuration(CrawlURL u) {
			long now = System.currentTimeMillis();
			long duration = (now - (Long) (u.getProcessorAttr(_CONNECT_ATTEMPT_MILLIS))) / 1000;
			return (int) duration;
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
					boolean isConnected = false;

					// try to finish connect,
					// 如果超时，抛出异常：java.net.ConnectException: Connection timed out
					// WIN7下默认超时时间经测试在20s左右
					// 在连接数很多时超时现象严重
					try {
						isConnected = channel.finishConnect();
					} catch (IOException e2) {
						// connect timed out
						logger.debug("连接服务器超时，距尝试连接时刻(s)：{},url：{},重试次数：{}",
								new Object[] { getConAttemptDuration(url), url, url.getRetryTimes(), e2 });
						failAndResumePipeline(key);
						return;
					}

					// connect successed
					if (isConnected) {
						url.setHandlerAttr(_CONNECT_SUCCESS_MILLIS, System.currentTimeMillis());
						key.cancel();
						try {
							sendHttpRequest(channel, url);
							nextReactor().register(channel, url);
						} catch (IOException e) {
							// send http request failed
							logger.debug("发送http请求失败，url：{},重试次数：{}", new Object[] { url, url.getRetryTimes(), e });
							failAndResumePipeline(key);
						}
						return;
					}

					// connect failed，较少见
					logger.debug("连接服务器失败，url：{},重试次数：{}", new Object[] { url, url.getRetryTimes() });
					failAndResumePipeline(key);
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
