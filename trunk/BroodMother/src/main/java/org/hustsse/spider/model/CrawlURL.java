package org.hustsse.spider.model;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hustsse.spider.dns.Dns;
import org.hustsse.spider.framework.Pipeline;
import org.hustsse.spider.util.httpcodec.HttpResponse;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 待爬取的URL，要么从Frontier调度出来进行爬取，要么作为Candidate处理之后进入Frontier，
 * 每个CrawlURL都会被一个Pipeline处理。
 *
 * <p>
 * CrawlURL使用{@link URL}类表示URL本身的内容，此外还保存着被Pipeline处理过程中所需的各种数据和状态。
 * </p>
 *
 * @author Anderson
 *
 */
public class CrawlURL implements Comparable<CrawlURL> {
	/** 用于处理该CrawlURL的Pipeline */
	@JsonIgnore
	private Pipeline pipeline;
	/** 存储Handler在处理过程中的某些动态数据 */
	@JsonIgnore
	private Map<String, Object> handlerAttrs = new HashMap<String, Object>();
	/** 页面抓取后的Http响应 */
	@JsonIgnore
	private HttpResponse response;
	/** Http响应的状态码 */
	@JsonIgnore
	private int responseStatusCode = 0;
	/** 候选子Url，如从页面中的a、frame等标签解析而得 */
	@JsonIgnore
	private List<CrawlURL> candidates;

	/*--- candidate attrs*/
	/** 是否测试通过，可以入frontier */
	private boolean allowed = false;
	/** inner URL */
	private URL url;
	/** 在哪个url上发现的 */
	private URL via;

	/**
	 * base url used to derelative url found in the content
	 * 如果有base标签，则以之为准；否则返回{@link #url}
	 */
	@JsonIgnore
	private URL baseURL;

	/** canonicalize 后的标准化形式，用于url判重 */
	private String canonicalStr;
	/** CrawlURL被schedule到frontier时的序号，所有URL参与排序，从0开始递增 */
	private long ordinal;
	/** CrawlURL所在WorkQueue的key */
	private String workQueueKey;
	/**
	 * CrawlURL在WorkQueue内部的优先级，提供了5个预设的优先级(HIGHEST/HIGH/NORMAL/LOW/LOWEST)。
	 * URL的优先级在各WorkQueue之间互相独立互不影响。值越小优先级越高。
	 * */
	private int priority = NORMAL;
	public static final int LOWEST = Integer.MAX_VALUE;
	public static final int NORMAL = LOWEST / 2;
	public static final int HIGHEST = 0;
	public static final int LOW = NORMAL + NORMAL / 2;
	public static final int HIGH = NORMAL / 2;

	/** DNS */
	@JsonIgnore
	private Dns dns;

	/** 页面fetch状态，暂时只有3个状态[ing/successed/failed]，视需求以后再增加  */
	@JsonIgnore
	private int fetchStatus = 0;
	public static final int FETCH_ING = 2;
	public static final int FETCH_SUCCESSED = 3;
	public static final int FETCH_FAILED = 4;

	/** 默认重试3次 */
	public static final int DEFAULT_RETRY_TIMES = 3;

	/** 是否是种子 */
	private boolean seed;
	/** 重试次数 */
	private int retryTimes;
	/** 是否需要重试 */
	private boolean needRetry;

	/**
	 * 通过Url和Pipeline构造一个CrawlURL对象。
	 *
	 * @param url
	 * @param pipeline 用于处理该CrawlURL的Pipeline
	 * @throws MalformedURLException 如果URL格式不合法
	 */
	public CrawlURL(String url, Pipeline pipeline) throws MalformedURLException {
		this(new URL(url), pipeline);
	}

	/**
	 * 通过Url和Pipeline构造一个CrawlURL对象。
	 * @param url
	 * @param pipeline 用于处理该CrawlURL的Pipeline
	 */
	public CrawlURL(URL url, Pipeline pipeline) {
		this.url = url;
		pipeline.attachTo(this);
		candidates = new ArrayList<CrawlURL>(20);
	}

	/**
	 * 通过Url创建一个CrawlURL对象，pipeline为空。
	 * @param url
	 * @throws MalformedURLException
	 */
	public CrawlURL(String url) throws MalformedURLException {
		this(new URL(url));
	}

	/**
	* 通过Url创建一个CrawlURL对象，pipeline为空。
	* @param url
	*/
	@JsonCreator
	public CrawlURL(@JsonProperty("url") URL url) {
		this.url = url;
		candidates = new ArrayList<CrawlURL>(20);
	}

	/**
	 * 通过Url创建一个CrawlURL对象，并指明其发现地。
	 * @param url
	 * @param via 在哪个Url上发现的
	 * @throws MalformedURLException
	 */
	public CrawlURL(String url, URL via) throws MalformedURLException {
		this(url);
		this.via = via;
	}

	/**
	 * 添加一个Candidate
	 * @param c
	 */
	public void addCandidate(CrawlURL c) {
		this.candidates.add(c);
	}

	public List<CrawlURL> getCandidates() {
		return this.candidates;
	}

	public Pipeline getPipeline() {
		return pipeline;
	}

	public void setPipeline(Pipeline pipeline) {
		this.pipeline = pipeline;
	}

	public String toString() {
		return url.toString();
	}

	/**
	 * 从handler attributes属性集合中移除某个key及其对应的属性。
	 * @param attrKey 要移除的属性
	 */
	public void removeHandlerAttr(String attrKey) {
		handlerAttrs.remove(attrKey);
	}

	/**
	 * 设置某个handler attribute
	 * @param key
	 * @param val
	 */
	public void setHandlerAttr(String key, Object val) {
		this.handlerAttrs.put(key, val);
	}

	/**
	 * 通过key得到某个handler attribute
	 * @param key
	 * @return
	 */
	public Object getHandlerAttr(String key) {
		return this.handlerAttrs.get(key);
	}

	public HttpResponse getResponse() {
		return response;
	}

	public void setResponse(HttpResponse response) {
		this.response = response;
		responseStatusCode = response.getStatus().getCode();
	}

	/**
	 * 得到base url。某个CrawlURL上发现的所有Candidate，如果是相对路径，
	 * 都将使用该base url进行解析。
	 * @return 返回{@link #baseURL}，如果baseURL为空，则返回{@link url}
	 */
	public URL getBaseURL() {
		if (this.baseURL != null) {
			return baseURL;
		}
		return url;
	}

	/**
	 * 设置用来derelative的基准url
	 * @param baseURL
	 */
	public void setBaseURL(URL baseURL) {
		this.baseURL = baseURL;
	}

	/**
	 * get the inner {@link URL} object.
	 * @return
	 */
	public URL getURL() {
		return url;
	}

	public int getResponseStatusCode() {
		return responseStatusCode;
	}

	public void setResponseStatusCode(int statusCode) {
		this.responseStatusCode = statusCode;
	}

	public boolean isAllowed() {
		return allowed;
	}

	public void setAllowed(boolean allowed) {
		this.allowed = allowed;
	}

	public String getCanonicalStr() {
		return canonicalStr;
	}

	public void setCanonicalStr(String canon) {
		canonicalStr = canon;
	}

	public void setOrdinal(long ordinal) {
		this.ordinal = ordinal;
	}

	public long getOrdinal() {
		return ordinal;
	}

	public String getWorkQueueKey() {
		return workQueueKey;
	}

	public void setWorkQueueKey(String workQueueKey) {
		this.workQueueKey = workQueueKey;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	@Override
	// 实现Comparable接口以便在WorkQueue中排序，首先根据Priority，priority一样时早发现的早出队
	public int compareTo(CrawlURL o) {
		// 优先级相等时按入frontier顺序排序
		if (o.priority == priority)
			return o.ordinal - this.ordinal > 0 ? 1 : -1;
		// 值越小优先级越高
		int d = o.getPriority() - priority;
		return (d == 0) ? 0 : ((d < 0) ? -1 : 1);
	}

	public boolean isSeed() {
		return seed;
	}

	public void setSeed(boolean seed) {
		this.seed = seed;
	}

	public int getRetryTimes() {
		return retryTimes;
	}

	public void setRetryTimes(int retryTimes) {
		this.retryTimes = retryTimes;
	}

	public void incRetryTimes() {
		retryTimes++;
	}

	public void setNeedRetry(boolean b) {
		this.needRetry = b;
	}

	public boolean isNeedRetry() {
		return needRetry;
	}

	public URL getVia() {
		return via;
	}

	public void setVia(URL via) {
		this.via = via;
	}

	public int getFetchStatus() {
		return fetchStatus;
	}

	public void setFetchStatus(int fetchStatus) {
		this.fetchStatus = fetchStatus;
	}

	public Dns getDns() {
		return dns;
	}

	public void setDns(Dns dns) {
		this.dns = dns;
	}

}
