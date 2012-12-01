package org.hustsse.spider.handler.crawl;

import static org.hustsse.spider.model.CrawlURL.FETCH_FAILED;

import org.apache.commons.lang3.StringUtils;
import org.hustsse.spider.dns.Dns;
import org.hustsse.spider.dns.DnsCache;
import org.hustsse.spider.framework.HandlerContext;
import org.hustsse.spider.handler.AbstractBeanNameAwareHandler;
import org.hustsse.spider.model.CrawlURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

/**
 * DNS解析器，在抓取某个CrawlURL之前负责解析其DNS，解析的DNS将被缓存在本地。
 *
 * @see DnsCache
 * @author Anderson
 *
 */
public class DnsResolver extends AbstractBeanNameAwareHandler {
	private static Logger logger = LoggerFactory.getLogger(DnsResolver.class);

	/** parameters for lookup by DNSJAVA.jar*/
	private short ClassType = DClass.IN;
	private short TypeType = Type.A;

	/** dns cache used */
	@Autowired
	DnsCache dnsCache;

	@Override
	public void process(HandlerContext ctx, CrawlURL url) {
		String host = url.getURL().getHost();

		// 没有host，不重试。不应该发生
		if (StringUtils.isEmpty(host)) {
			logger.debug("\"url没有host？\"+url");
			failAndDontRetry(ctx, url);
			return;
		}

		// 从缓存中找
		Dns cachedDns = dnsCache.get(host);
		if (cachedDns != null) {
			url.setDns(cachedDns);
			ctx.proceed();
			return;
		}

		// 缓存中没有，lookup
		Record[] recordSet = null;
		String lookupName = host.endsWith(".") ? host : host + ".";
		try {
			recordSet = (new Lookup(lookupName, TypeType, ClassType)).run();
		} catch (TextParseException e) {
		}

		// lookup有结果
		if (recordSet != null) {
			ARecord arecord = getFirstARecord(recordSet);
			// lookup失败，不重试
			if (arecord == null) {
				logger.debug("查询到Null ARecord，url：" + url);
				failAndDontRetry(ctx, url);
				return;
			}
			// lookup成功
			long ttl = arecord.getTTL();
			Dns dns = new Dns(host, arecord.getAddress(), ttl > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ttl);
			url.setDns(dns);
			dnsCache.put(host, dns);
			ctx.proceed();
			return;
		}

		// lookup没有结果
		// TODO localhost？本地host文件？
		failAndDontRetry(ctx, url);
	}

	/**
	 * finish & fail the pipeline , do not need retry
	 * @param ctx
	 * @param url
	 */
	private void failAndDontRetry(HandlerContext ctx, CrawlURL url) {
		url.setFetchStatus(FETCH_FAILED);
		url.setNeedRetry(false);
		ctx.finish();
	}

	private ARecord getFirstARecord(Record[] recordSet) {
		ARecord arecord = null;
		if (recordSet == null || recordSet.length == 0) {
			return arecord;
		}
		for (Record record : recordSet) {
			if (record.getType() != Type.A)
				continue;
			arecord = (ARecord) record;
			break;
		}
		return arecord;
	}

}
