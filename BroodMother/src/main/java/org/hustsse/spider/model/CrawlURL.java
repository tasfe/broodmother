package org.hustsse.spider.model;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hustsse.spider.framework.Dns;
import org.hustsse.spider.framework.Pipeline;
import org.hustsse.spider.handler.crawl.fetcher.httpcodec.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.annotation.JSONCreator;
import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class CrawlURL implements Comparable<CrawlURL> {
	@JsonIgnore
	private Pipeline pipeline;
	@JsonIgnore
	private Map<String, Object> processorAttrs = new HashMap<String, Object>();
	@JsonIgnore
	private HttpResponse response;
	@JsonIgnore
	private List<CrawlURL> candidates;
	@JsonIgnore
	private int responseStatusCode = 0;

	/*--- candidate attrs*/
	/** 是否测试通过，可以入frontier */
	private boolean allowed = false;

	private URL url;
	/** 在哪个url上发现的 */
	private URL via;

	public static void main(String[] args) throws JsonGenerationException, JsonMappingException, IOException {
		CrawlURL u = new CrawlURL("http://www.baidu.com");
//		String s = JSON.toJSONString(u);
//		System.out.println(s);

		URL uu = new URL("http://www.google.com");
//		String s1 = JSON.toJSONString(uu);
//		System.out.println(s1);
//		URL u2 = JSON.parseObject(s1,URL.class);
//		System.out.println(u2);

		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		mapper.writeValue(new File("R:\\result.json"), uu);
		mapper.writeValue(new File("R:\\result2.json"), u);

		URL value = mapper.readValue(new File("R:\\result.json"), URL.class);
		CrawlURL value2 = mapper.readValue(new File("R:\\result2.json"), CrawlURL.class);
		System.out.println(value);
		System.out.println(value2);

//		CrawlURL u1 = JSON.parseObject(s,CrawlURL.class);
//		System.out.println(u1);

	}

	/**
	 * base url used to derelative url found in the content
	 * 如果有base标签，则以之为准；否则返回自己
	 */
	@JsonIgnore
	private URL baseURL;

	/** canonicalize 后的标准化形式，用于url判重 */
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

	@JsonIgnore
	private Dns dns;

	/** 页面fetch状态 */
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
	/** 是否抓取失败，需要重试 */
	private boolean needRetry;

	public CrawlURL(String url, Pipeline pipeline) throws MalformedURLException {
		this(new URL(url), pipeline);
	}

	public CrawlURL(URL url, Pipeline pipeline) {
		this.url = url;
		pipeline.attachTo(this);
		candidates = new ArrayList<CrawlURL>(20);
	}

	public CrawlURL(String url) throws MalformedURLException {
		this(new URL(url));
	}

	@JsonCreator
	public CrawlURL(@JsonProperty("url") URL url) {
		this.url = url;
		candidates = new ArrayList<CrawlURL>(20);
	}

	public CrawlURL(String url, URL via) throws MalformedURLException {
		this(url);
		this.via = via;
	}

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

	public void removeProcessorAttr(String requestBuffer) {
		processorAttrs.remove(requestBuffer);
	}

	public void setHandlerAttr(String key, Object val) {
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
		responseStatusCode = response.getStatus().getCode();
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
