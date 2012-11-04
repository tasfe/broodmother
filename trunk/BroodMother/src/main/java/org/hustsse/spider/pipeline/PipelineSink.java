package org.hustsse.spider.pipeline;

import org.hustsse.spider.exception.PipelineException;
import org.hustsse.spider.model.URL;


public interface PipelineSink {
	
    void uriSunk(URL e);
    
    void exceptionCaught(URL e, PipelineException cause) ;
    
}
