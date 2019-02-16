package mways.com.preference;

import android.app.Application;
import android.content.Context;

public class AppliCationClass extends Application {
    static  AppliCationClass context;



    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
    }
    public static synchronized AppliCationClass getInstance() {
        return context;
    }
}
