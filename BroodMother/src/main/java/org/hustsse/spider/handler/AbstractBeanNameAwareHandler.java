package org.hustsse.spider.handler;

import org.hustsse.spider.framework.Handler;
import org.springframework.beans.factory.BeanNameAware;

/**
 * 抽象Handler基础实现，使用Bean Name作为自己在Pipeline中的名称。
 *
 * @author Anderson
 *
 */
public abstract class AbstractBeanNameAwareHandler implements Handler,BeanNameAware{

	/** handler名称 */
	protected String handlerName;

	@Override
	public void setBeanName(String beanName) {
		handlerName = beanName;
	}

	@Override
	public String getName() {
		return handlerName;
	}

}
