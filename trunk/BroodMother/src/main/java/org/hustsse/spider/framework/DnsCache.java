package org.hustsse.spider.framework;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

public class DnsCache {

	public final static CacheManager cacheManager = new CacheManager();
	private String cacheName;
	private Cache cache;

	public DnsCache(String cacheName) {
		this.cacheName = cacheName;
		cache = cacheManager.getCache(cacheName);
		if (cache == null)
			throw new IllegalArgumentException("找不到名为'" + cacheName + "'的缓存！");
	}

	public Dns get(String host) {
		Element element = cache.get(host);
		return element == null ? null : (Dns) (element.getValue());
	}

	public void put(String host, Dns dns) {
		// dns为0表示不应该缓存
		if (dns.getTtl() <= 0) {
			return;
		}
		Element e = new Element(host, dns, false, 0, dns.getTtl() == Dns.NEVER_EXPIRES ? 0 : dns.getTtl());
		cache.put(e);
	}

	public void clear() {
		cache.removeAll();
	}

	public String getCacheName() {
		return cacheName;
	}

	public Cache getCache() {
		return cache;
	}
}
