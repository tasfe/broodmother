package org.hustsse.spider.util;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class CommonUtils {

	public static void toFile(ByteBuffer b,String file) {
		FileOutputStream out;
		try {
			out = new FileOutputStream(file);
			FileChannel localFile = out.getChannel();
			localFile.write(b);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void toFile(String s,String file) {
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


}
