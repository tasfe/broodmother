package org.hustsse.spider.handler;

import org.hustsse.spider.framework.Handler;
import org.springframework.beans.factory.BeanNameAware;

public abstract class AbstractBeanNameAwareHandler implements Handler,BeanNameAware{

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
