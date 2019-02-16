package mways.com.preference.network;

import android.content.Intent;

import java.io.IOException;

import mways.com.preference.AppliCationClass;
import mways.com.preference.PreferenceManager;
import okhttp3.Interceptor;
import okhttp3.Response;

public class NetworkInterceptor implements Interceptor{
    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Response response;
        if (!new ServiceManager(AppliCationClass.getInstance()).isNetworkAvailable()) {
            throw (new IOException("No Internet"));
        } else {
            response = chain.proceed(chain.request());
            if (response.message().contains("Unauthorized")) {
                PreferenceManager.deleteAll(AppliCationClass.getInstance());
                AppliCationClass.getInstance().sendBroadcast(new Intent("Unauthorized"));
            }
        }
        return response;
    }
}
