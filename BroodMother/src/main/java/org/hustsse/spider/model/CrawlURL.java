package org.hustsse.spider.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hustsse.spider.framework.Pipeline;
import org.hustsse.spider.handler.crawl.fetcher.httpcodec.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrawlURL implements Comparable<CrawlURL>{
	static Logger logger = LoggerFactory.getLogger(CrawlURL.class);
	private Pipeline pipeline;
	private Map<String, Object> processorAttrs = new HashMap<String, Object>();
	private HttpResponse response;
	private List<CrawlURL> candidateUrls;

	private int fetchStatus = 0;

	/*--- candidate attrs*/
	/** 是否测试通过，可以入frontier */
	private Boolean passedRules = null;

	private URL url;

	/**
	 * base url used to derelative url found in the content
	 * 如果有base标签，则以之为准；否则返回自己
	 */
	private URL baseURL;

	/** canonicalize 后的标准化形式，用于url判重*/
	private String canonicalStr;
	/** CrawlURL被schedule到frontier时的序号，所有URL参与排序，递增 */
	private long ordinal;
	/** CrawlURL所在WorkQueue的key */
	private String workQueueKey;
	/**
	 * CrawlURL在WorkQueue内部的优先级，提供了5个预设的优先级(HIGHEST/HIGH/NORMAL/LOW/LOWEST)。
	 * URL的优先级在各WorkQueue之间是互相独立互不影响的。值越小优先级越高。
	 * */
	private int priority = NORMAL;
	public static final int LOWEST = Integer.MAX_VALUE;
	public static final int NORMAL = LOWEST / 2;
	public static final int HIGHEST = 0;
	public static final int LOW = NORMAL + NORMAL / 2;
	public static final int HIGH = NORMAL / 2;

	/** 是否是种子 */
	private boolean seed;


	public CrawlURL(String url, Pipeline pipeline) {
		this(new URL(url), pipeline);
	}

	public CrawlURL(URL url, Pipeline pipeline) {
		this.url = url;
		pipeline.attachTo(this);
		candidateUrls = new ArrayList<CrawlURL>(20);
	}

	public CrawlURL(String url) {
		this(new URL(url));
	}

	public CrawlURL(URL url) {
		this.url = url;
		candidateUrls = new ArrayList<CrawlURL>(20);
	}

	public void addCandidate(CrawlURL c) {
		this.candidateUrls.add(c);
	}

	public List<CrawlURL> getCandidates() {
		return this.candidateUrls;
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

	public void setProcessorAttr(String key, Object val) {
		this.processorAttrs.put(key, val);
	}

	public Object getProcessorAttr(String key) {
		return this.processorAttrs.get(key);
	}

	public HttpResponse getResponse() {
		return response;
	}

	public void setResponse(HttpResponse response) {
		this.response = response;
		fetchStatus = response.getStatus().getCode();
	}

	// crawlurl加一个构造器： relative path，base uri。在链接抽取，构造outlink的时候，1. 判断是否相对路径
	// 2.判断是否有base标签，没有则baseuri = source uri。
	public URL getBaseURL() {
		if (this.baseURL != null) {
			return baseURL;
		}
		return url;
	}

	public void setBaseURL(URL baseURL) {
		this.baseURL = baseURL;
	}

	public URL getURL() {
		return url;
	}

	public int getFetchStatus() {
		return fetchStatus;
	}

	public void setFetchStatus(int fetchStatus) {
		this.fetchStatus = fetchStatus;
	}

	public boolean isPassedRules() {
		return passedRules;
	}

	public void setPassedRules(boolean passedRules) {
		this.passedRules = passedRules;
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
	public int compareTo(CrawlURL o) {
		// 优先级相等时按入frontier顺序排序
		if(o.priority == priority)
			return o.ordinal - this.ordinal > 0?1:-1;
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
}
