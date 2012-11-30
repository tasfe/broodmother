package org.hustsse.spider.util;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.hustsse.spider.model.CrawlURL;

public class CommonUtils {

	public static void toFile(ByteBuffer b,String file, CrawlURL url) {
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(file);
			FileChannel localFile = out.getChannel();
			if(url != null)
				localFile.write(ByteBuffer.wrap((url.toString()+"\n\n").getBytes()));
			localFile.write(b);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally {
			if(out != null)
				try {
					out.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}

	}

	public static void writeToFile(String s,String file) {
		FileWriter w = null;
		try {
			w = new FileWriter(file);
			w.write(s);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally {
			if(w!=null)
				try {
					w.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	}

	public static void appendToFile(String s,String file) {
		FileWriter w = null;
		try {
			w = new FileWriter(file);
			w.append(s);
			w.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally {
			if(w!=null)
				try {
					w.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	}


}
