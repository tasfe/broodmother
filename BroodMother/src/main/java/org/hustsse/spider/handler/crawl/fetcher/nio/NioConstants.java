package org.hustsse.spider.handler.crawl.fetcher.nio;

public class NioConstants {
	/**
	 * 连接超时，默认10s
	 */
	public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 30 * 1000;

	/*
	 * processor attr keys
	 */

	/**
	 * 连接超时时刻，存储在CrawlURI的processorAttrs中。当某channel超过该时间还未连接上
	 * 即判定为超时。
	 */
	public static final String _CONNECT_DEADLINE_NANOS = "connect deadline nanos";


	//TODO 移入CrawlURI？毕竟其他Fetch实现也有可能需要跟踪其fetch状态
	public static final String _FETCH_STATUS = "fetch status";
	public static final Integer FETCH_ING = 2;
	public static final Integer FETCH_FINISHED = 3;
	public static final Integer FETCH_ERROR = 4;
	public static final String _RAW_RESPONSE = "raw response";


}
