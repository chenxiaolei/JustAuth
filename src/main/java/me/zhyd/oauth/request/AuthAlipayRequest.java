package me.zhyd.oauth.request;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipaySystemOauthTokenRequest;
import com.alipay.api.request.AlipayUserInfoShareRequest;
import com.alipay.api.response.AlipaySystemOauthTokenResponse;
import com.alipay.api.response.AlipayUserInfoShareResponse;
import me.zhyd.oauth.cache.AuthStateCache;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.config.AuthDefaultSource;
import me.zhyd.oauth.enums.AuthUserGender;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.utils.StringUtils;
import me.zhyd.oauth.utils.UrlBuilder;

import java.util.function.Function;

/**
 * 支付宝登录
 *
 * @author yadong.zhang (yadong.zhang0415(a)gmail.com)
 * @since 1.0.1
 */
public class AuthAlipayRequest extends AuthDefaultRequest {

    private AlipayClient alipayClient;

    public AuthAlipayRequest(AuthConfig config) {
        super(config, AuthDefaultSource.ALIPAY);
        this.alipayClient = new DefaultAlipayClient(AuthDefaultSource.ALIPAY.accessToken(), config.getClientId(), config.getClientSecret(), "json", "UTF-8", config
            .getAlipayPublicKey(), "RSA2");
    }

    public AuthAlipayRequest(AuthConfig config, AuthStateCache authStateCache) {
        super(config, AuthDefaultSource.ALIPAY, authStateCache);
        this.alipayClient = new DefaultAlipayClient(AuthDefaultSource.ALIPAY.accessToken(), config.getClientId(), config.getClientSecret(), "json", "UTF-8", config
            .getAlipayPublicKey(), "RSA2");
    }

    @Override
    protected AuthToken getAccessToken(AuthCallback authCallback, Function<String, String> redirectUriProcess) {
        AlipaySystemOauthTokenRequest request = new AlipaySystemOauthTokenRequest();
        request.setGrantType("authorization_code");
        request.setCode(authCallback.getAuth_code());
        AlipaySystemOauthTokenResponse response = null;
        try {
            response = this.alipayClient.execute(request);
        } catch (Exception e) {
            throw new AuthException(e);
        }
        if (!response.isSuccess()) {
            throw new AuthException(response.getSubMsg());
        }
        return AuthToken.builder()
            .accessToken(response.getAccessToken())
            .uid(response.getUserId())
            .expireIn(Integer.parseInt(response.getExpiresIn()))
            .refreshToken(response.getRefreshToken())
            .build();
    }

    @Override
    protected AuthUser getUserInfo(AuthToken authToken, Function<String, String> redirectUriProcess) {
        String accessToken = authToken.getAccessToken();
        AlipayUserInfoShareRequest request = new AlipayUserInfoShareRequest();
        AlipayUserInfoShareResponse response = null;
        try {
            response = this.alipayClient.execute(request, accessToken);
        } catch (AlipayApiException e) {
            throw new AuthException(e.getErrMsg(), e);
        }
        if (!response.isSuccess()) {
            throw new AuthException(response.getSubMsg());
        }

        String province = response.getProvince(), city = response.getCity();
        String location = String.format("%s %s", StringUtils.isEmpty(province) ? "" : province, StringUtils.isEmpty(city) ? "" : city);

        return AuthUser.builder()
            .uuid(response.getUserId())
            .username(StringUtils.isEmpty(response.getUserName()) ? response.getNickName() : response.getUserName())
            .nickname(response.getNickName())
            .avatar(response.getAvatar())
            .location(location)
            .gender(AuthUserGender.getRealGender(response.getGender()))
            .token(authToken)
            .source(source.toString())
            .build();
    }


    @Override
    public String authorize(String state, Function<String, String> redirectUriProcess) {

        return UrlBuilder.fromBaseUrl(source.authorize())
            .queryParam("app_id", config.getClientId())
            .queryParam("scope", "auth_user")
            .queryParam("redirect_uri", redirectUriProcess.apply(config.getRedirectUri()))
            .queryParam("state", getRealState(state))
            .build();
    }
}
