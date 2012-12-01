package org.hustsse.spider.handler.crawl.fetcher.nio;

import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants.DEFAULT_CONNECT_TIMEOUT_MILLIS;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants.WRITE_SPIN_COUNT;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._CONNECT_ATTEMPT_MILLIS;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._CONNECT_DEADLINE_NANOS;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._CONNECT_SUCCESS_MILLIS;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._LAST_SEND_REQUEST_MILLIS;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._RAW_RESPONSE;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._REQUEST_ALREADY_SEND_SIZE;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._REQUEST_BUFFER;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._REQUEST_SEND_FINISHED;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._REQUEST_SEND_TIMES;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._REQUEST_SIZE;
import static org.hustsse.spider.model.CrawlURL.FETCH_FAILED;
import static org.hustsse.spider.model.CrawlURL.FETCH_ING;
import static org.hustsse.spider.model.CrawlURL.FETCH_SUCCESSED;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
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
import org.hustsse.spider.framework.HandlerContext;
import org.hustsse.spider.framework.Pipeline;
import org.hustsse.spider.handler.AbstractBeanNameAwareHandler;
import org.hustsse.spider.model.CrawlURL;
import org.hustsse.spider.util.HttpMessageUtil;
import org.hustsse.spider.util.httpcodec.DefaultHttpRequest;
import org.hustsse.spider.util.httpcodec.HttpHeaders;
import org.hustsse.spider.util.httpcodec.HttpMethod;
import org.hustsse.spider.util.httpcodec.HttpRequest;
import org.hustsse.spider.util.httpcodec.HttpResponse;
import org.hustsse.spider.util.httpcodec.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 下载器，用于下载网页，基于JDK的NIO实现，没有用Netty等框架，遵循了Reactor模式，这一点是模仿Netty的。
 * <p>
 * <h1>Reactor模式</h1>
 * 两种角色：Boss和Reactor，Boss负责监听连接的Connect状态，当连接上时将其转移注册到Reactor，后者负责连接的读写。
 *
 * <h1>Pipeline pause&resume机制的使用</h1>
 * 当连接注册到Boss时，NioFetcher会将Pipeline挂起(pause)，NioFetcher将成为断点；
 * 当Reactor接收到数据后resume
 * Pipeline，并将数据当做Message传递至Pipeline，Pipeline从上次断点处继续运行，重新进入NioFetcher
 * ；若CrawlURL并未爬取完 ，NioFetcher保存当前数据片并继续挂起pipeline，等待下次被Reactor
 * resume；若CrawlURL数据读取完毕，则对所有数据进行合并、解析，并在最后proceed
 * pipeline，CrawlURL流入下一个handler被处理。
 *
 * <p>
 * TODO：提供OIO/Netty/httpclient的版本。
 *
 * @author Anderson
 *
 */
public class NioFetcher extends AbstractBeanNameAwareHandler {
	private static final Logger logger = LoggerFactory.getLogger(NioFetcher.class);
	/** 默认Reactor数量，=处理器核数*2 */
	static final int DEFAULT_REACTOR_NUMS = Runtime.getRuntime().availableProcessors() * 2;
	/** 默认Boss数量 */
	static final int DEFAULT_BOSS_NUMS = 1;
	private Reactor[] reactors;
	private Boss[] boss;
	private int curReactorIndex;
	private int curBossIndex;

	/** Boss线程池 */
	@SuppressWarnings("unused")
	private Executor bossExecutor;
	/** Reactor线程池 */
	@SuppressWarnings("unused")
	private Executor reactorExecutor;

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
		this.bossExecutor = bossExecutor;
		this.reactorExecutor = reactorExecutor;

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
		// 初始进入NioFetcher，并未注册到Boss或Reactor。
		if (msg == null) {
			try {
				SocketChannel channel = SocketChannel.open();
				channel.configureBlocking(false);

				// 构造服务器地址，DNS已经在上一级的DnsResolver中被解析
				SocketAddress reomteAddress = new InetSocketAddress(url.getDns().getIp(), url.getURL().getPort());

				// 发起connect请求，若立即connect成功，成功则注册到Reactor
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
					// 立即connect失败则注册到Boss，监听其OP_CONNECT状态
					url.setHandlerAttr(_CONNECT_ATTEMPT_MILLIS, System.currentTimeMillis());
					long curNano = System.nanoTime();
					// 设置超时时刻为当前时间+NioConstants.DEFAULT_CONNECT_TIMEOUT_MILLIS
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
		case FETCH_FAILED: // 抓取失败，重试并finish pipeline
			url.setNeedRetry(true);
			ctx.finish();
			return;
		case FETCH_ING: // 抓取未完成，保存数据片并pause pipeline
			appendToSegList((ByteBuffer) msg, url);
			url.getPipeline().clearMessage();
			ctx.pause();
			return;
		case FETCH_SUCCESSED: // 抓取成功，合并数据片并解析成http响应，proceed pipeline
			ByteBuffer segment = (ByteBuffer) msg;
			List<ByteBuffer> responseSegments = appendToSegList(segment, url);
			ByteBuffer merged = merge(responseSegments);
			merged.flip(); // *** 设置为写出模式 ***

			/*
			 * raw http
			 * response(ByteBuffer形式，未解析)和解析之后的HttpResponse（但是Content依然是ByteBuffer形式
			 * ）， are backed by the SAME buffer,be careful to modify them
			 */
			url.setHandlerAttr(_RAW_RESPONSE, merged); // 后续processor会用到原始http响应么？不需要的话可以删除之
			HttpResponse response = HttpMessageUtil.decodeHttpResponse(merged.duplicate());
			url.setResponse(response);

			// TODO 对content生成消息摘要，用于对内容判重
			ctx.proceed();
			return;
		default:
			return;
		}
	}

	/**
	 * 将读到的Http响应片段添加到URI对应的segment list末尾，segment list作为handler attr保存在
	 * {@link CrawlURL#handlerAttrs}中，key为{@link NioConstants#_RAW_RESPONSE}。
	 *
	 * @param segment
	 *            http响应数据片
	 * @param uriProcessed
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private List<ByteBuffer> appendToSegList(ByteBuffer segment, CrawlURL uriProcessed) {
		Object val = uriProcessed.getHandlerAttr(_RAW_RESPONSE);
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

	/**
	 * merge http响应数据片。
	 *
	 * TODO:Netty的CompositeChannelBuffer性能更好。
	 *
	 * @param buffers
	 * @return
	 */
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
	 * 组装并发送http请求。
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

		ByteBuffer reqBuffer = HttpMessageUtil.encodeHttpRequest(httpRequest);
		// 一些统计信息
		url.setHandlerAttr(_REQUEST_SIZE, reqBuffer.capacity());
		url.setHandlerAttr(_REQUEST_ALREADY_SEND_SIZE, 0);
		url.setHandlerAttr(_REQUEST_SEND_TIMES, 0);

		// 发送http request
		// 为了防止在网络高负载情况下无法写入Socket内核缓冲区（几乎不会发生，毕竟http请求一般很小），自旋若干次。
		int writtenBytes = 0;
		for (int i = WRITE_SPIN_COUNT; i > 0; i--) {
			writtenBytes = channel.write(reqBuffer);
			if (writtenBytes != 0) {
				url.setHandlerAttr(_LAST_SEND_REQUEST_MILLIS, System.currentTimeMillis());
				url.setHandlerAttr(_REQUEST_ALREADY_SEND_SIZE, writtenBytes);
				url.setHandlerAttr(_REQUEST_SEND_TIMES, (Integer) url.getHandlerAttr(_REQUEST_SEND_TIMES) + 1);
				break;
			}
		}
		// 99%的情况都会一次性发送完毕，不会注册到reactor上，但是以防万一还是做点处理。
		boolean reqSendFinished = !reqBuffer.hasRemaining();
		url.setHandlerAttr(_REQUEST_SEND_FINISHED, reqSendFinished);
		// save the request buffer for next sending
		if (!reqSendFinished) {
			url.setHandlerAttr(_REQUEST_BUFFER, reqBuffer);
		}
	}

	/**
	 * 获取下一个使用的Boss，round-robbin方式使用所有boss
	 * @return
	 */
	public Boss nextBoss() {
		return boss[curBossIndex++ % boss.length];
	}

	/**
	 * 获取下一个使用的Reactor，round-robbin方式使用所有reactor
	 * @return
	 */
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
				Long connectDeadlineNanos = (Long) u.getHandlerAttr(_CONNECT_DEADLINE_NANOS);
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
			long duration = (now - (Long) (u.getHandlerAttr(_CONNECT_ATTEMPT_MILLIS))) / 1000;
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
