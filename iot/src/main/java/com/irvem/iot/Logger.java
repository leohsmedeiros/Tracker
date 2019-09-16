package com.irvem.iot;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Logger {
    private static String TAG = "iot";

    public static void d (String msg) {
        Log.d(TAG, msg);
    }
    public static void w (String msg) {
        Log.w(TAG, msg);
    }
    public static void e (String msg, Throwable throwable) {
        Log.e(TAG, msg, throwable);
    }
    public static void e (String msg) {
        Log.e(TAG, msg);
    }

    public static void logOnFile (String fileName, String msg) {
        try {
//            String secondPathName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + "/aws_iot.txt";
            String pathName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + "/" + fileName;
            File logFile = new File(pathName);

            if (!logFile.exists()) {
                boolean wasFileCreated = logFile.createNewFile();

                if (!wasFileCreated)
                    throw new IOException("logfile was not created");
            }

            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(msg);
            buf.newLine();
            buf.close();

        }catch (IOException e) {
            e.printStackTrace();
        }
    }
}
