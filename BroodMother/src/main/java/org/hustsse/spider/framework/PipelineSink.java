package org.hustsse.spider.framework;

import org.hustsse.spider.exception.PipelineException;
import org.hustsse.spider.model.CrawlURL;

//对crawlurl/cadidateurl的收尾工作在这里进行。开放给用户配置并提供一个默认的实现对于crawlurl，如果不用提供的CrawlURLSink。
//不管pipeline中怎么跳转，或者异常，这个是一定会进来的
public interface PipelineSink {

    void uriSunk(CrawlURL e);

    void exceptionCaught(CrawlURL e, PipelineException cause) ;

}
