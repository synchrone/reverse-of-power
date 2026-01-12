package android.util;

/**
 * Shadow implementation of android.util.Log for unit tests.
 * Redirects all log calls to System.out.println.
 */
public class Log {
    public static int v(String tag, String msg) {
        System.out.println("V/" + tag + ": " + msg);
        return 0;
    }

    public static int v(String tag, String msg, Throwable tr) {
        System.out.println("V/" + tag + ": " + msg + "\n" + tr);
        return 0;
    }

    public static int d(String tag, String msg) {
        System.out.println("D/" + tag + ": " + msg);
        return 0;
    }

    public static int d(String tag, String msg, Throwable tr) {
        System.out.println("D/" + tag + ": " + msg + "\n" + tr);
        return 0;
    }

    public static int i(String tag, String msg) {
        System.out.println("I/" + tag + ": " + msg);
        return 0;
    }

    public static int i(String tag, String msg, Throwable tr) {
        System.out.println("I/" + tag + ": " + msg + "\n" + tr);
        return 0;
    }

    public static int w(String tag, String msg) {
        System.out.println("W/" + tag + ": " + msg);
        return 0;
    }

    public static int w(String tag, String msg, Throwable tr) {
        System.out.println("W/" + tag + ": " + msg + "\n" + tr);
        return 0;
    }

    public static int w(String tag, Throwable tr) {
        System.out.println("W/" + tag + ": " + tr);
        return 0;
    }

    public static int e(String tag, String msg) {
        System.out.println("E/" + tag + ": " + msg);
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        System.out.println("E/" + tag + ": " + msg + "\n" + tr);
        return 0;
    }

    public static int wtf(String tag, String msg) {
        System.out.println("WTF/" + tag + ": " + msg);
        return 0;
    }

    public static int wtf(String tag, Throwable tr) {
        System.out.println("WTF/" + tag + ": " + tr);
        return 0;
    }

    public static int wtf(String tag, String msg, Throwable tr) {
        System.out.println("WTF/" + tag + ": " + msg + "\n" + tr);
        return 0;
    }

    public static boolean isLoggable(String tag, int level) {
        return true;
    }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null) return "";
        java.io.StringWriter sw = new java.io.StringWriter();
        tr.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    public static int println(int priority, String tag, String msg) {
        System.out.println(priority + "/" + tag + ": " + msg);
        return 0;
    }
}
