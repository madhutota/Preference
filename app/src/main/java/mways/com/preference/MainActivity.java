package mways.com.preference;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.google.gson.JsonObject;

import mways.com.preference.network.ApiClass;
import mways.com.preference.network.ApiClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static mways.com.preference.AppliCationClass.context;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }



    /*public void sendNotification(JsonObject chatObject) {
        ApiClass apiClass = ApiClient.getClient().create(ApiClass.class);
        Call<JsonObject> getotp = apiClass.sendNotification(Utility.getDeviceId(context),
                PreferenceManager.getString(context, AppConstants.userType, ""),
                PreferenceManager.getString(context, AppConstants.USER_ID, ""), chatObject);
        getotp.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) {

                } else {
                    new ErrorDialogue(context, "Something went wrong", "Please try again");
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                //  new ApiErrorDialogue(context, AppConstants.NO_INTERNET, t.getMessage()).show();
                //new ErrorDialogue(context, AppConstants.NO_INTERNET, t.getMessage());
                t.getCause();

            }
        });
    }*/

}
