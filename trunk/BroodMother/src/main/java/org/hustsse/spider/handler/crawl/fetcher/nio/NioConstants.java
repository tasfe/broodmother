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
	 * 连接超时时刻，存储在CrawlURI的processorAttrs中。当某channel超过该时间还未连接上 即判定为超时。
	 */
	public static final String _CONNECT_DEADLINE_NANOS = "connect deadline nanos";

	public static final String _RAW_RESPONSE = "raw response";

	public static final String _REQUEST_SEND_FINISHED = "request send finished";
	public static final String _REQUEST_SIZE = "request size";
	public static final String _REQUEST_SEND_TIMES = "request send times";
	public static final String _REQUEST_ALREADY_SEND_SIZE = "request already send size";
	public static final String _REQUEST_BUFFER = "request buffer";

	public static final int WRITE_SPIN_COUNT = 16;

	public static final String _LAST_SEND_REQUEST_MILLIS = "last send request millis";

	public static final String _REQUEST_SEND_FINISHED_MILLIS = "request send finished millis";

	public static final String _CONNECT_SUCCESS_MILLIS = "connect success millis";

	public static final String _CONNECT_ATTEMPT_MILLIS = "connect attempt millis";

}
