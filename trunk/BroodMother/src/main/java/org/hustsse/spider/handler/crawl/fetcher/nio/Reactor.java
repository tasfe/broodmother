package org.hustsse.spider.handler.crawl.fetcher.nio;

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
import org.hustsse.spider.model.CrawlURL;

public class Reactor implements Runnable {

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
	private int index;
	private Executor reactorExecutor;
	private boolean started;

	public Reactor(Executor reactorExecutor, int index) {
		this.reactorExecutor = reactorExecutor;
		this.index = index;
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
				channel.register(selector, SelectionKey.OP_READ, uri);
			} catch (ClosedChannelException e) {
				e.printStackTrace();
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
						processReadableKey(key);
					}
					// TODO shutdown
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
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
			while ((ret = channel.read(buffer)) > 0) {
				readBytes += ret;
				if (!buffer.hasRemaining()) {
					break;
				}
			}
			// 读取完毕了？设置URI的状态
			uri.setProcessorAttr(NioConstants._FETCH_STATUS, ret < 0 ? NioConstants.FETCH_FINISHED : NioConstants.FETCH_ING);
			// 若本次读到了数据，无论是否读取完毕均resume pipeline执行，并将读取到的数据传递出去
			if (readBytes > 0) {
				// 从DirectBuffer拷贝数据到一个compact的Heap ByteBuffer，传递出去
				ByteBuffer temp = ByteBuffer.allocate(buffer.position());
				buffer.flip();
				temp.put(buffer);
				uri.getPipeline().resume(temp);
			}

		} catch (IOException e) {
			e.printStackTrace();
			cancelAndClose(key);
			// TODO 读取响应失败，重试？
			uri.setProcessorAttr(NioConstants._FETCH_STATUS, NioConstants.FETCH_ERROR);
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
