/**
 * Copyright (c) 2016 Mattermost, Inc. All Rights Reserved.
 * See License.txt for license information.
 */
package com.mattermost.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.webkit.CookieManager;

import com.mattermost.mattermost.R;
import com.mattermost.model.User;
import com.mattermost.service.jacksonconverter.JacksonConverterFactory;
import com.mattermost.service.jacksonconverter.PromiseConverterFactory;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit.Retrofit;
import retrofit.http.Body;
import retrofit.http.POST;

public class MattermostService {

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static MattermostService service;
    private final WebkitCookieManagerProxy cookieStore;

    private final Context context;
    private final OkHttpClient client = new OkHttpClient();

    private Retrofit retrofit;
    private MattermostAPI apiClient;
    private SharedPreferences preferences;
    private String baseUrl;
    private String team = null;

    public MattermostService(Context context) {
        this.context = context;
        String userAgent = context.getResources().getString(R.string.app_user_agent);

        cookieStore = new WebkitCookieManagerProxy();

        client.setCookieHandler(cookieStore);
        preferences = context.getSharedPreferences("App", Context.MODE_PRIVATE);
    }

    public boolean isLoggedIn() {
        return preferences.getBoolean("loggedIn", false);
    }

    // all this is hax
    public String getToken(){
        String baseUrl = service.getBaseUrl();
        if (baseUrl == null) {
            return "";
        }

        String cookies = CookieManager.getInstance().getCookie(baseUrl);

        if (cookies == null)
            return "";
        if (cookies.trim().isEmpty())
            return "";

        Pattern r = Pattern.compile("(MMAUTHTOKEN|MMTOKEN)=([^\\;]+);?\\s?");
        Matcher m = r.matcher(cookies);

        if (m.find()) {
            return "BEARER " + m.group(2);
        }

        return "";
    }

    public String getBaseUrl() {
        baseUrl = "https://chat.ladbrokes.net.au/ladbrokes";
        if (baseUrl == null) {
            baseUrl = preferences.getString("baseUrl", null);
        }
        return baseUrl;
    }

    public void removeBaseUrl() {
        preferences.edit().remove("baseUrl").commit();
    }

    public void init(String baseUrl) {
        this.baseUrl = baseUrl;
        preferences.edit().putString("baseUrl", baseUrl).commit();

        client.networkInterceptors().add(new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request.Builder builder = chain.request().newBuilder();
                String token = getToken();
                if (token != "") {
                    builder.addHeader("Authorization", token);
                }
                return chain.proceed(builder.build());
            }
        });

        Retrofit.Builder builder = new Retrofit.Builder();
        builder.baseUrl(baseUrl);
        builder.client(client);
        builder.addConverterFactory(JacksonConverterFactory.create());
        builder.addCallAdapterFactory(PromiseConverterFactory.create());

        retrofit = builder.build();

        String url = baseUrl;
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        int i = url.lastIndexOf("/");
        if (i != -1) {
            String team = url.substring(i + 1);
            setTeam(team);
        }

        apiClient = retrofit.create(MattermostAPI.class);
    }

    public Promise<User> login(String email, String password) {
        User user = new User();
        user.name = getTeam();
        user.email = email;
        user.password = password;

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String deviceId = sharedPreferences.getString("device_id", null);
        user.deviceId = "android:" + deviceId.toString();

        return apiClient.login(user);
    }

    public Promise<User> attachDevice() {
        User user = new User();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String deviceId = sharedPreferences.getString("device_id", null);
        user.deviceId = "android:" + deviceId.toString();

        //return apiClient.attachDevice(user);
        return apiClient.attachDevice(user);
    }

    public Promise<Boolean> findTeamByName(String name) {
        User user = new User();
        user.name = name;
        return apiClient.findTeamByName(user);
    }

    public Promise<User> signup(String email, String name) {
        User user = new User();
        user.email = email;
        user.name = name;
        return apiClient.signup(user);
    }

    public OkHttpClient getClient() {
        return client;
    }

    public Promise<User> forgotPassword(String emailAddress) {
        User user = new User();
        user.email = emailAddress;
        return apiClient.sendPaswordReset(user);
    }

    public String getTeam() {
        if (team == null) {
            team = preferences.getString("Team", "");
        }

        return team;
    }

    public void setTeam(String name) {
        if (name == null) {
            preferences.edit().remove("Team").commit();
        } else {
            preferences.edit().putString("Team", name).commit();
        }

        team = name;
    }

    public boolean isAttached() {
        return "true".equals(preferences.getString("AttachedId", "false"));
    }

    public void SetAttached() {
        preferences.edit().putString("AttachedId", "true").commit();
    }

    public void logout() {
        preferences.edit().remove("AttachedId").commit();
        preferences.edit().remove("Team").commit();
        preferences.edit().remove("baseUrl").commit();
        preferences.edit().remove("loggedIn").commit();
        cookieStore.clear();
    }

    public interface MattermostAPI {
        @POST("/api/v1/users/attach_device")
        Promise<User> attachDevice(@Body User user);

        @POST("/api/v1/users/login")
        Promise<User> login(@Body User user);

        @POST("/api/v1/users/send_password_reset")
        Promise<User> sendPaswordReset(@Body User user);

        @POST("/api/v1/teams/find_team_by_name")
        Promise<Boolean> findTeamByName(@Body User user);

        @POST("/api/v1/teams/email_teams")
        Promise<List<User>> findTeams(@Body User user);

        @POST("/api/v1/teams/signup")
        Promise<User> signup(@Body User user);
    }
}
