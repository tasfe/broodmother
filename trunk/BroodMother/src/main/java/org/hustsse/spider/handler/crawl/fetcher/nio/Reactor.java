package org.hustsse.spider.handler.crawl.fetcher.nio;

import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants.WRITE_SPIN_COUNT;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._CONNECT_SUCCESS_MILLIS;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._LAST_SEND_REQUEST_MILLIS;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._REQUEST_ALREADY_SEND_SIZE;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._REQUEST_BUFFER;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._REQUEST_SEND_FINISHED;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._REQUEST_SEND_FINISHED_MILLIS;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._REQUEST_SEND_TIMES;
import static org.hustsse.spider.handler.crawl.fetcher.nio.NioConstants._REQUEST_SIZE;
import static org.hustsse.spider.model.CrawlURL.FETCH_FAILED;
import static org.hustsse.spider.model.CrawlURL.FETCH_ING;
import static org.hustsse.spider.model.CrawlURL.FETCH_SUCCESSED;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

import org.hustsse.spider.exception.ReactorException;
import org.hustsse.spider.framework.DefaultPipeline;
import org.hustsse.spider.model.CrawlURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Reactor implements Runnable {

	Logger logger = LoggerFactory.getLogger(Reactor.class);

	private static final int DEFAULT_RECEIVE_BUFFER_SIZE = 30 * 1024;
	static final int MAX_CONNECT_RETRY_TIMES = 3;
	/**
	 * 接收网络数据使用的Direct Buffer，每次都会重用这一个。
	 * 读取到的数据将会被拷贝到一个 Heap ByteBuffer并传递给Pipeline。
	 */
	private ByteBuffer receiveBuffer;

	private String threadName;
	private Queue<Runnable> registerQueue = new LinkedBlockingQueue<Runnable>();
	private Selector selector;
	private Executor reactorExecutor;
	private boolean started;

	public Reactor(Executor reactorExecutor, int index) {
		this.reactorExecutor = reactorExecutor;
		threadName = "New I/O reactor线程 #" + index;
		this.receiveBuffer = ByteBuffer.allocateDirect(DEFAULT_RECEIVE_BUFFER_SIZE);
	}

	public void register(SocketChannel channel, CrawlURL uri) {
		if (!started) {
			// Open a selector if this worker didn't start yet.
			try {
				this.selector = Selector.open();
			} catch (Throwable t) {
				throw new ReactorException("Failed to create a selector.", t);
			}

			// Start the worker thread with the new Selector.

			reactorExecutor.execute(this);
			started = true;
		}

		RegisterTask task = new RegisterTask(channel, uri);
		registerQueue.offer(task);
	}

	class RegisterTask implements Runnable {
		SocketChannel channel;
		int tryConnectTimes;
		CrawlURL uri;

		public RegisterTask(SocketChannel channel, CrawlURL uri) {
			this.channel = channel;
			this.uri = uri;
		}

		@Override
		public void run() {
			try {
				// 如果http请求没有发送完毕，我们还需要监听OP_WRITE状态
				Boolean requestSendFinished = (Boolean)uri.getHandlerAttr(_REQUEST_SEND_FINISHED);
				if(Boolean.TRUE.equals(requestSendFinished)) {
					channel.register(selector, SelectionKey.OP_READ, uri);
				}else {
					channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, uri);
				}
			} catch (ClosedChannelException e) {
				// channel由于某些原因关闭了，比如发送http request失败等。忽略之
			}
		}
	}

	@Override
	public void run() {
		Thread.currentThread().setName(threadName);
		while (started) {
			processRegisterTaskQueue();
			try {
				if (selector.select(1000) > 0) {
					Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
					while (iter.hasNext()) {
						SelectionKey key = iter.next();
						iter.remove();
						// 处理Key
						if(key.isReadable()) {
							processReadableKey(key);
						}else if(key.isWritable()){
							processWritableKey(key);
						}
					}
					// TODO shutdown
				}
			} catch (IOException e) {
				logger.warn("Unexpected exception in the selector loop.", e);

				// Prevent possible consecutive immediate failures that lead to
				// excessive CPU consumption.
				try {
					Thread.sleep(1000);
				} catch (InterruptedException t) {
					// Ignore.
				}
			}
		}
	}

	private void processWritableKey(SelectionKey key) {
		CrawlURL url = (CrawlURL)key.attachment();
		SocketChannel channel = (SocketChannel)key.channel();
		ByteBuffer buffer = (ByteBuffer)url.getHandlerAttr(_REQUEST_BUFFER);
		try {
			// 发送http请求，若发送完成，取消OP_WRITE。
			int writtenBytes = 0;
			for (int i = WRITE_SPIN_COUNT; i > 0; i--) {
				writtenBytes = channel.write(buffer);
				//write success
				if (writtenBytes != 0) {
					url.setHandlerAttr(_LAST_SEND_REQUEST_MILLIS, System.currentTimeMillis());
					url.setHandlerAttr(_REQUEST_ALREADY_SEND_SIZE, (Integer)url.getHandlerAttr(_REQUEST_ALREADY_SEND_SIZE) + writtenBytes);
					url.setHandlerAttr(_REQUEST_SEND_TIMES, (Integer)url.getHandlerAttr(_REQUEST_SEND_TIMES) + 1);
					break;
				}
			}
			boolean reqSendFinished = !buffer.hasRemaining();
			url.setHandlerAttr(_REQUEST_SEND_FINISHED, reqSendFinished);
			url.setHandlerAttr(_REQUEST_SEND_FINISHED_MILLIS, reqSendFinished);
			if(reqSendFinished) {
				url.removeHandlerAttr(_REQUEST_BUFFER);
				key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
			}
		} catch (IOException e) {
			logger.error("error send http request ! URL: "+url);
			cancelAndClose(key);
			url.setFetchStatus(FETCH_FAILED);
			url.getPipeline().resume(DefaultPipeline.EMPTY_MSG);
		}
	}

	private void processReadableKey(SelectionKey key) {
		/**
		 * <pre>
		 * 这里暂时使用一连串的Heap-based ByteBuffer来保存每次读取到的网页数据，
		 *
		 * TODO：
		 * 1. 使用directByteBuffer，bytebuffer池化/一次性分配大的然后slice？
		 * 2. 读大小预测器 -- 保存在CrawURI中，每服务器一个
		 * 3. resume时传递的message不用bytebuffer，拷贝到一个相同大小的HeapChannelBuffer中; byteBuffer只在执行真正的网络IO时使用
		 * 4. CrawURI中使用一个composite channel buffer，每次将第三步中的buffer合并进去，减少拷贝次数
		 * </pre>
		 */
		ByteBuffer buffer = this.receiveBuffer;
		buffer.clear();
		SocketChannel channel = (SocketChannel) key.channel();
		CrawlURL uri = (CrawlURL) key.attachment();

		int ret = 0;
		int readBytes = 0;
		try {
			while ((ret = channel.read(buffer)) > 0) {	// 在低速网络情况下会抛出：java.io.IOException: 远程主机强迫关闭了一个现有的连接。
				readBytes += ret;
				if (!buffer.hasRemaining()) {
					break;
				}
			}
			// 读取完毕了？设置URI的状态
			uri.setFetchStatus(ret < 0 ? FETCH_SUCCESSED : FETCH_ING);
			// 若本次读到了数据，无论是否读取完毕均resume pipeline执行，并将读取到的数据传递出去
			if (readBytes > 0) {
				// 从DirectBuffer拷贝数据到一个compact的Heap ByteBuffer，传递出去
				ByteBuffer msg = ByteBuffer.allocate(buffer.position());
				buffer.flip();
				msg.put(buffer);
				uri.getPipeline().resume(msg);
			}

		} catch (IOException e) {
			Object lastSendTime = uri.getHandlerAttr(_LAST_SEND_REQUEST_MILLIS);
			Long conTime = (Long)uri.getHandlerAttr(_CONNECT_SUCCESS_MILLIS);
			Integer sendReqTimes = (Integer)uri.getHandlerAttr(_REQUEST_SEND_TIMES);
			Integer sendBytes = (Integer)uri.getHandlerAttr(_REQUEST_ALREADY_SEND_SIZE);
			Integer requestSize = (Integer)uri.getHandlerAttr(_REQUEST_SIZE);
			long now = System.currentTimeMillis();
			String debug = "\n";
			if(lastSendTime != null) {
				debug += "距上次发送request时间（s）："+((now - (Long)lastSendTime)/1000);
				debug += "\n一共发送request次数："+ sendReqTimes;
				debug += "\n一共发送字节："+ sendBytes;
				debug += "\n请求共有字节："+ requestSize;
			}else {
				debug += "未发送过request，距连接成功时间（s）："+((now - conTime)/1000);
			}
			logger.error("error read http response ! URL: "+uri+debug,e);
			cancelAndClose(key);
			// TODO 读取响应失败，重试？
			uri.setFetchStatus(FETCH_FAILED);
			uri.getPipeline().resume(DefaultPipeline.EMPTY_MSG);
		}

		// 如果数据读取完毕，取消注册，关闭连接
		if (ret < 0) {
			cancelAndClose(key);
		}
	}

	/**
	 * cancel the key registion and close the channel
	 *
	 * @param k
	 */
	private void cancelAndClose(SelectionKey k) {
		k.cancel();
		try {
			k.channel().close();
		} catch (IOException e) {
			e.printStackTrace();
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
}
