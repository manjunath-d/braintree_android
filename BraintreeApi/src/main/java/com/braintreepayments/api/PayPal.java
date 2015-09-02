package com.braintreepayments.api;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.util.Log;

import com.braintreepayments.api.exceptions.BraintreeException;
import com.braintreepayments.api.exceptions.ConfigurationException;
import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.interfaces.HttpResponseCallback;
import com.braintreepayments.api.interfaces.PaymentMethodResponseCallback;
import com.braintreepayments.api.models.Configuration;
import com.braintreepayments.api.models.PayPalAccountBuilder;
import com.braintreepayments.api.models.PayPalCheckout;
import com.braintreepayments.api.models.PayPalConfiguration;
import com.braintreepayments.api.models.PayPalPaymentResource;
import com.braintreepayments.api.models.PaymentMethod;
import com.paypal.android.sdk.onetouch.core.AuthorizationRequest;
import com.paypal.android.sdk.onetouch.core.BrowserSwitchAdapter;
import com.paypal.android.sdk.onetouch.core.CheckoutRequest;
import com.paypal.android.sdk.onetouch.core.PayPalOneTouchActivity;
import com.paypal.android.sdk.onetouch.core.PayPalOneTouchCore;
import com.paypal.android.sdk.onetouch.core.PerformRequestStatus;
import com.paypal.android.sdk.onetouch.core.Request;
import com.paypal.android.sdk.onetouch.core.RequestTarget;
import com.paypal.android.sdk.onetouch.core.Result;
import com.paypal.android.sdk.onetouch.core.ResultType;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Class containing PayPal specific logic.
 */
public class PayPal {

    public static final int PAYPAL_AUTHORIZATION_REQUEST_CODE = 13591;

    // Supported scopes for PayPal authorization
    public static final String SCOPE_FUTURE_PAYMENTS = "https://uri.paypal.com/services/payments/futurepayments";
    public static final String SCOPE_EMAIL = "email";
    public static final String SCOPE_ADDRESS = "address";

    protected static boolean sEnableSignatureVerification = true;

    private static final String PAYLOAD_KEY_CLIENT_TOKEN = "client_token";

    private static Request sPendingRequest;
    private static PerformRequestStatus sPendingRequestStatus;
    private static BraintreeFragmentBroadcastReceiver sBraintreeFragmentBroadcastReceiver =
            new BraintreeFragmentBroadcastReceiver();

    /**
     * Starts the Pay With PayPal flow. This will launch the PayPal app if installed or switch to
     * the browser for user authorization. The Billing Agreement flow will be used if enabled,
     * otherwise the Future Payment flow will be used.
     *
     * @param fragment A {@link BraintreeFragment} used to process the request.
     */
    public static void authorizeAccount(BraintreeFragment fragment) {
        authorizeAccount(fragment, null);
    }

    /**
     * Starts the Pay With PayPal flow with additional scopes. This will launch the PayPal app if
     * installed or switch to the browser for user authorization. The Billing Agreement flow will be
     * used if enabled, otherwise the Future Payment flow will be used.
     *
     * @param fragment A {@link BraintreeFragment} used to process the request.
     * @param additionalScopes A {@link java.util.List} of additional scopes. Ex: {@link #SCOPE_ADDRESS}.
     *                         Acceptable scopes are defined in {@link com.braintreepayments.api.PayPal}.
     */
    public static void authorizeAccount(final BraintreeFragment fragment, final List<String> additionalScopes) {
        fragment.sendAnalyticsEvent("paypal.selected");

        /*
         * Use billing agreements if enabled, we are moving away from Future Payment flow
         */
        if (fragment.getConfiguration().getPayPal().getUseBillingAgreement()) {
            PayPalCheckout checkout = new PayPalCheckout();
            billingAgreement(fragment, checkout);
            return;
        }

        if (fragment != null) {
            sBraintreeFragmentBroadcastReceiver.setFragment(fragment);
            BraintreeBroadcastManager.getInstance(fragment.getActivity())
                    .registerReceiver(sBraintreeFragmentBroadcastReceiver, new IntentFilter(
                            BraintreeBrowserSwitchActivity.LOCAL_BROADCAST_BROWSER_SWITCH_COMPLETED));
        }
        try {
            sPendingRequest = buildPayPalAuthorizationConfiguration(fragment.getActivity(),
                            fragment.getConfiguration(),
                            fragment.getClientToken().toJson());

            if (additionalScopes != null) {
                for (String scope : additionalScopes) {
                    ((AuthorizationRequest) sPendingRequest).withScopeValue(scope);
                }
            }

            sPendingRequestStatus =
                    PayPalOneTouchCore.performRequest(fragment.getActivity(),
                            sPendingRequest,
                            PAYPAL_AUTHORIZATION_REQUEST_CODE,
                            sEnableSignatureVerification,
                            new BrowserSwitchAdapter() {
                                @Override
                                public void handleBrowserSwitchIntent(Intent intent) {
                                    fragment.getActivity().startActivityForResult(
                                            new Intent(fragment.getActivity(),
                                                    BraintreeBrowserSwitchActivity.class)
                                                    .setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                                    .putExtra(
                                                            BraintreeBrowserSwitchActivity.EXTRA_INTENT,
                                                            intent),
                                            PAYPAL_AUTHORIZATION_REQUEST_CODE);
                                }
                            });
            sendAnalyticsForPayPalPerformRequestStatus(fragment, sPendingRequestStatus,
                    false);
        } catch (BraintreeException ignored) {
        }
    }

    /**
     * Starts the Billing Agreement flow for PayPal. This will launch the PayPal app if installed or
     * fall back to a browser switch.
     *
     * @param fragment A {@link BraintreeFragment} used to process the request.
     * @param checkout A {@link PayPalCheckout} used to customize the request.
     */
    public static void billingAgreement(BraintreeFragment fragment, PayPalCheckout checkout) {
        PayPal.checkout(fragment, checkout, true);
    }

    /**
     * Starts the Single Payment flow for PayPal. This will launch the PayPal app if installed or
     * fall back to a browser switch.
     *
     * @param fragment A {@link BraintreeFragment} used to process the request.
     * @param checkout A {@link PayPalCheckout} used to customize the request. An amount MUST be
     * specified.
     */
    public static void checkout(BraintreeFragment fragment, PayPalCheckout checkout) {
        if (checkout.getAmount() == null) {
            throw new RuntimeException("An amount MUST be specified for the Single Payment flow.");
        }
        PayPal.checkout(fragment, checkout, false);
    }

    /**
     * Starts the Checkout With PayPal flow. This will launch the PayPal app if installed or switch
     * to the browser for user authorization.
     *
     * @param fragment A {@link BraintreeFragment} used to process the request.
     * @param checkout A {@link PayPalCheckout} used to customize the request.
     * @param isBillingAgreement A boolean. If true, this will use the Billing Agreement. Otherwise,
     * PayPal will perform a Single Payment.
     */
    private static void checkout(final BraintreeFragment fragment, final PayPalCheckout checkout,
            final boolean isBillingAgreement) {
        sBraintreeFragmentBroadcastReceiver.setFragment(fragment);
        BraintreeBroadcastManager.getInstance(fragment.getActivity())
                .registerReceiver(sBraintreeFragmentBroadcastReceiver, new IntentFilter(
                        BraintreeBrowserSwitchActivity.LOCAL_BROADCAST_BROWSER_SWITCH_COMPLETED));

        HttpResponseCallback callback = new HttpResponseCallback() {
            @Override
            public void success(String responseBody) {
                final PayPalPaymentResource paypalPaymentResource;
                try {
                    paypalPaymentResource = PayPalPaymentResource.fromJson(responseBody);
                } catch (JSONException e) {
                    // If the PayPalPaymentResource is unable to be loaded, return.
                    Log.d("PayPal", "Unable to parse URL for PayPal app switch.");
                    return;
                }
                //OTC browser/app switching requires that it be run on the main thread
                fragment.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            sPendingRequest =
                                    buildPayPalCheckoutConfiguration(
                                            paypalPaymentResource.getRedirectUrl(),
                                            fragment.getActivity(),
                                            fragment.getConfiguration());

                            sPendingRequestStatus =
                                    PayPalOneTouchCore.performRequest(fragment.getActivity(),
                                            sPendingRequest,
                                            PAYPAL_AUTHORIZATION_REQUEST_CODE,
                                            sEnableSignatureVerification,
                                            new BrowserSwitchAdapter() {
                                                @Override
                                                public void handleBrowserSwitchIntent(
                                                        Intent intent) {
                                                    fragment.getActivity().startActivityForResult(
                                                            new Intent(fragment.getActivity(),
                                                                    BraintreeBrowserSwitchActivity.class)
                                                                    .setFlags(
                                                                            Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                                                    .putExtra(
                                                                            BraintreeBrowserSwitchActivity.EXTRA_INTENT,
                                                                            intent),
                                                            PAYPAL_AUTHORIZATION_REQUEST_CODE);
                                                }
                                            });

                            sendAnalyticsForPayPalPerformRequestStatus(fragment,
                                    sPendingRequestStatus, true);
                        } catch (ConfigurationException ignored) {
                        }
                    }
                });
            }

            @Override
            public void failure(Exception ignored) {
                //Do nothing, for now
                ErrorWithResponse ex = (ErrorWithResponse)ignored;
                try {
                    JSONObject response = new JSONObject(ex.getErrorResponse());
                    String debugId = response.getJSONObject("error").getString("debugId");
                    Log.d("PayPal", "Unable to generate URL for PayPal app switch. DebugId: " + debugId);
                } catch (JSONException e) {
                    Log.d("PayPal", "Unable to generate URL for PayPal app switch.");
                }
            }
        };

        try {
            createPayPalPaymentResource(fragment, checkout, isBillingAgreement, callback);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ErrorWithResponse errorWithResponse) {
            errorWithResponse.printStackTrace();
        } catch (BraintreeException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create a PayPalPaymentResource on behalf of the merchant. To be used in the PayPal Checkout
     * flows for Single Payment and Billing Agreement.
     *
     * @param fragment A {@link BraintreeFragment} used to process the request.
     * @param checkout A {@link PayPalCheckout} used to customize the request.
     * @param isBillingAgreement A boolean. If true, this will use the Billing Agreement. Otherwise,
     * PayPal will perform a Single Payment.
     * @param callback A callback on the http request.
     */
    private static void createPayPalPaymentResource(BraintreeFragment fragment,
            PayPalCheckout checkout, boolean isBillingAgreement, HttpResponseCallback callback)
            throws JSONException, ErrorWithResponse, BraintreeException {
        Configuration configuration = fragment.getConfiguration();
        CheckoutRequest request =
                PayPal.buildPayPalCheckoutConfiguration(null, fragment.getContext(), configuration);

        String currencyCode = checkout.getCurrencyCode();
        if (currencyCode == null) {
            currencyCode = configuration.getPayPal().getCurrencyIsoCode();
        }

        JSONObject experienceProfile = new JSONObject();
        experienceProfile.put("no_shipping", !checkout.getEnableShippingAddress());
        experienceProfile.put("address_override", checkout.getAddressOverride());

        if (checkout.getLocaleCode() != null) {
            experienceProfile.put("locale_code", checkout.getLocaleCode());
        }

        String authorizationFingerprint = fragment.getClientToken().getAuthorizationFingerprint();
        JSONObject parameters = new JSONObject()
                .put("authorization_fingerprint", authorizationFingerprint)
                .put("return_url", request.getSuccessUrl())
                .put("cancel_url", request.getCancelUrl())
                .put("experience_profile", experienceProfile);

        if (!isBillingAgreement) {
            parameters.put("amount", checkout.getAmount().toString())
                    .put("currency_iso_code", currencyCode);
        }

        if (checkout.getAddressOverride() && checkout.getShippingAddress() != null) {
            parameters.put("line1", checkout.getShippingAddress().getStreetAddress());
            parameters.put("line2", checkout.getShippingAddress().getExtendedAddress());
            parameters.put("city", checkout.getShippingAddress().getLocality());
            parameters.put("state", checkout.getShippingAddress().getRegion());
            parameters.put("postal_code", checkout.getShippingAddress().getPostalCode());
            parameters.put("country_code", checkout.getShippingAddress().getCountryCodeAlpha2());
            parameters.put("recipient_name", checkout.getShippingAddress().getRecipientName());
        }

        String apiUrl = isBillingAgreement ? "paypal_hermes/setup_billing_agreement" :
                "paypal_hermes/create_payment_resource";
        String versionedPath = "/v1/" + apiUrl;
        fragment.getHttpClient().post(
                versionedPath,
                parameters.toString(),
                callback
        );
    }

    /**
     * Finish the PayPal flow. Create a {@link com.braintreepayments.api.models.PayPalAccount}.
     * Parses the URL using the OTC library and attempts to tokenize the result.
     *
     * @param fragment A {@link BraintreeFragment} used to process the request.
     * @param resultCode Result code from the PayPal flow.
     * @param data Intent returned from PayPal flow. Contains the URL to process.
     */
    public synchronized static void finishPayWithPayPal(final BraintreeFragment fragment,
            int resultCode, Intent data) {
        try {
            BraintreeBroadcastManager.getInstance(fragment.getActivity()).unregisterReceiver(
                    sBraintreeFragmentBroadcastReceiver);
            Result result = PayPal.getResultFromActivity(fragment.getActivity(), resultCode, data);
            Boolean isCheckout = PayPal.isCheckoutRequest();
            Boolean isAppSwitch = data.hasExtra(PayPalOneTouchActivity.EXTRA_ONE_TOUCH_RESULT);

            ResultType resultType = result.getResultType();
            switch (resultType) {
                case Error:
                    sendAnalyticsEventForSwitchResult(fragment, isCheckout, isAppSwitch, "failed");
                    break;
                case Cancel:
                    if (result.getError() == null) {
                        sendAnalyticsEventForSwitchResult(fragment, isCheckout, isAppSwitch,
                                "canceled");
                    } else {
                        sendAnalyticsEventForSwitchResult(fragment, isCheckout, isAppSwitch,
                                "canceled-with-error");
                    }
                    break;
                case Success:
                    sendAnalyticsEventForSwitchResult(fragment, isCheckout, isAppSwitch,
                            "succeeded");
                    PayPalAccountBuilder paypalAccountBuilder =
                            getBuilderFromResponse(fragment.getContext(), resultCode, data);
                    if (paypalAccountBuilder != null) {
                        PaymentMethodTokenization.tokenize(fragment, paypalAccountBuilder,
                                new PaymentMethodResponseCallback() {
                                    @Override
                                    public void success(PaymentMethod paymentMethod) {
                                        fragment.postCallback(paymentMethod);
                                    }

                                    @Override
                                    public void failure(Exception exception) {
                                        fragment.postCallback(exception);
                                    }
                                });
                    }
                    break;
            }
        } catch (ConfigurationException ignored) {}
    }

    /**
     * The result from PayPal's request.
     *
     * @param fragment A {@link BraintreeFragment} used to process the request.
     * @param resultCode The result code from the request.
     * @param data Data associated with the result.
     */
    protected static void onActivityResult(final BraintreeFragment fragment, int resultCode,
            Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            if (isPayPalIntent(data)) {
                PayPal.finishPayWithPayPal(fragment, resultCode, data);
            }
        }
    }

    /**
     * Parse the PayPal response URL using OneTouchCore.
     *
     * @param context Context that received the result.
     * @param resultCode Result code returned in result.
     * @param intent The {@link Intent} returned in result.
     * @return A {@link PayPalAccountBuilder} or null if the intent is invalid.
     */
    private static PayPalAccountBuilder getBuilderFromResponse(Context context, int resultCode,
            Intent intent) throws ConfigurationException {

        if (resultCode != Activity.RESULT_OK || context == null) {
            throw new ConfigurationException(
                    "Cannot return PayPalAccountBuilder with invalid context or resultCode");
        }

        PayPalAccountBuilder paypalAccountBuilder = new PayPalAccountBuilder()
                .clientMetadataId(sPendingRequestStatus.getClientMetadataId());

        Result result = getResultFromActivity(context, resultCode, intent);
        if (intent.hasExtra(PayPalOneTouchActivity.EXTRA_ONE_TOUCH_RESULT)) {
            paypalAccountBuilder.source("paypal-app");
        } else {
            paypalAccountBuilder.source("paypal-browser");
        }

        ResultType resultType = result.getResultType();
        switch (resultType) {
            case Error:
                return null;
            case Cancel:
                return null;
            case Success:
                JSONObject response = result.getResponse();

                // Modify payload in 'mock' mode to scope the response
                try {
                    if (response.optJSONObject("client").optString("environment").equalsIgnoreCase(
                            AuthorizationRequest.ENVIRONMENT_MOCK)
                            && response.optJSONObject("response").optString("code") != null
                            && !isCheckoutRequest()) {
                        response.put("response", new JSONObject()
                                .put("code", "fake-code:" + ((AuthorizationRequest)
                                        sPendingRequest).getScopeString()));
                    }
                } catch (JSONException ignored) {}

                paypalAccountBuilder.oneTouchCoreData(response);
                break;
        }

        return paypalAccountBuilder;
    }

    /**
     * Retrieve the {@link Result} associated with the {@link Request}.
     *
     * @param context Context that received the result.
     * @param resultCode Result code returned in result.
     * @param intent The {@link Intent} returned in result.
     * @return the {@link Result} of the OTC app/browser switch.
     * @throws ConfigurationException
     */
    private static Result getResultFromActivity(Context context, int resultCode,
            Intent intent) throws ConfigurationException {
        if (resultCode != Activity.RESULT_OK || context == null) {
            throw new ConfigurationException(
                    "Cannot return PayPalAccountBuilder with invalid context or resultCode");
        }

        Result result;
        if (intent.hasExtra(PayPalOneTouchActivity.EXTRA_ONE_TOUCH_RESULT)) {
            result = intent.getParcelableExtra(
                    PayPalOneTouchActivity.EXTRA_ONE_TOUCH_RESULT);
        } else {
            result = PayPalOneTouchCore.handleBrowserResponse(context, intent.getData(),
                    sPendingRequest);
        }

        return result;
    }

    /**
     * Check if the current/last request was a CheckoutRequest
     */
    private static Boolean isCheckoutRequest() {
        return sPendingRequest != null && sPendingRequest instanceof CheckoutRequest;
    }

    /**
     * Check if the intent is indeed a PayPal intent
     */
    private static boolean isPayPalIntent(Intent intent) {
        return intent.hasExtra(PayPalOneTouchActivity.EXTRA_ONE_TOUCH_RESULT);
    }

    /**
     * Set properties specific to an CheckoutRequest
     */
    private static CheckoutRequest buildPayPalCheckoutConfiguration(String approvalUrl,
            Context context,
            Configuration configuration) throws ConfigurationException {
        validatePayPalConfiguration(configuration);

        String pairingId = null;
        if (approvalUrl != null) {
            pairingId = Uri.parse(approvalUrl).getQueryParameter("token");
        }
        return populateCommonData(new CheckoutRequest(), context, configuration)
                .pairingId(pairingId)
                .approvalURL(approvalUrl);
    }

    /**
     * Set properties specific to an AuthorizationRequest
     */
    private static AuthorizationRequest buildPayPalAuthorizationConfiguration(Context context,
            Configuration configuration, String clientKeyString) throws ConfigurationException {
        PayPalConfiguration payPalConfiguration = configuration.getPayPal();
        validatePayPalConfiguration(configuration);

        return populateCommonData(new AuthorizationRequest(context), context, configuration)
                .privacyUrl(payPalConfiguration.getPrivacyUrl())
                .userAgreementUrl(payPalConfiguration.getUserAgreementUrl())
                .withScopeValue(SCOPE_FUTURE_PAYMENTS)
                .withScopeValue(SCOPE_EMAIL)
                .withAdditionalPayloadAttribute(PAYLOAD_KEY_CLIENT_TOKEN,
                        clientKeyString);
    }

    /**
     * Set properties of a Request that are common to both CheckoutRequest and AuthorizationRequest
     */
    private static <T extends Request> T populateCommonData(T request, Context context,
            Configuration configuration) {
        PayPalConfiguration payPalConfiguration = configuration.getPayPal();
        String environment;
        if (payPalConfiguration.getEnvironment().equals("live")) {
            environment = AuthorizationRequest.ENVIRONMENT_LIVE;
        } else if (payPalConfiguration.getEnvironment().equals("offline")) {
            environment = AuthorizationRequest.ENVIRONMENT_MOCK;
        } else {
            environment = payPalConfiguration.getEnvironment();
        }

        String clientId = payPalConfiguration.getClientId();
        if (clientId == null && environment == AuthorizationRequest.ENVIRONMENT_MOCK) {
            clientId = "FAKE-PAYPAL-CLIENT-ID";
        }

        request.environment(environment);
        request.clientId(clientId);
        request.cancelUrl(context.getPackageName() + ".braintree", "cancel");
        request.successUrl(context.getPackageName() + ".braintree", "success");
        return request;
    }

    /**
     * Throws a {@link ConfigurationException} when the config is invalid
     *
     * @param configuration
     * @throws ConfigurationException
     */
    private static void validatePayPalConfiguration(Configuration configuration)
            throws ConfigurationException {
        PayPalConfiguration payPalConfiguration = configuration.getPayPal();

        if (!configuration.isPayPalEnabled() ||
                payPalConfiguration.getEnvironment() == null ||
                payPalConfiguration.getPrivacyUrl() == null ||
                payPalConfiguration.getUserAgreementUrl() == null) {
            throw new ConfigurationException("PayPal is disabled or configuration is invalid");
        }
    }

    /**
     * Send analytics for PayPal app switching.
     *
     * @param fragment A {@link BraintreeFragment} used to process the request.
     * @param requestStatus the {@link PerformRequestStatus} returned by PayPal OTC
     * @param isCheckout is this a single payment request
     */
    private static void sendAnalyticsForPayPalPerformRequestStatus(BraintreeFragment fragment,
            PerformRequestStatus requestStatus, Boolean isCheckout) {
        String eventFragment = "";
        if (isCheckout) {
            if (requestStatus == null) {
                eventFragment = "paypal-single-payment.none.initiate.failed";
            } else {
                if (requestStatus.isSuccess() &&
                        requestStatus.getTarget() == RequestTarget.browser) {
                    eventFragment = "paypal-single-payment.webswitch.initiate.started";
                } else if (!requestStatus.isSuccess() &&
                        requestStatus.getTarget() == RequestTarget.browser) {
                    eventFragment = "paypal-single-payment.webswitch.initiate.failed";
                } else if (requestStatus.isSuccess() &&
                        requestStatus.getTarget() == RequestTarget.wallet) {
                    eventFragment = "paypal-single-payment.appswitch.initiate.started";
                } else if (!requestStatus.isSuccess() &&
                        requestStatus.getTarget() == RequestTarget.wallet) {
                    eventFragment = "paypal-single-payment.appswitch.initiate.failed";
                }
            }
        } else {
            if (requestStatus == null) {
                eventFragment = "paypal-future-payments.none.initiate.failed";
            } else {
                if (requestStatus.isSuccess() &&
                        requestStatus.getTarget() == RequestTarget.browser) {
                    eventFragment = "paypal-future-payments.webswitch.initiate.started";
                } else if (!requestStatus.isSuccess() &&
                        requestStatus.getTarget() == RequestTarget.browser) {
                    eventFragment = "paypal-future-payments.webswitch.initiate.failed";
                } else if (requestStatus.isSuccess() &&
                        requestStatus.getTarget() == RequestTarget.wallet) {
                    eventFragment = "paypal-future-payments.appswitch.initiate.started";
                } else if (!requestStatus.isSuccess() &&
                        requestStatus.getTarget() == RequestTarget.wallet) {
                    eventFragment = "paypal-future-payments.appswitch.initiate.failed";
                }
            }
        }
        AnalyticsManager.sendRequest(fragment, "custom", eventFragment);
    }

    /**
     * Send analytics for PayPal app switch result.
     *
     * @param fragment A {@link BraintreeFragment} used to process the request.
     * @param isCheckout True if the request is a Single Payment. False if Billing Agreement.
     * @param isAppSwitch True if the request switched to the PayPal app. False if browser switch.
     * @param eventFragment A {@link String} describing the result.
     */
    private static void sendAnalyticsEventForSwitchResult(BraintreeFragment fragment,
            boolean isCheckout, boolean isAppSwitch, String eventFragment) {
        String authorizationType = isCheckout ? "paypal-single-payment" : "paypal-future-payments";
        String switchType = isAppSwitch ? "appswitch" : "webswitch";
        String event = String.format("%s.%s.%s", authorizationType, switchType, eventFragment);
        AnalyticsManager.sendRequest(fragment, "custom", event);
    }

    /**
     * A BroadcastReceiver that has access to a {@link BraintreeFragment}.
     */
    private static class BraintreeFragmentBroadcastReceiver extends BroadcastReceiver {

        private BraintreeFragment fragment;

        public void setFragment(BraintreeFragment fragment) {
            this.fragment = fragment;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            int browserResultCode =
                    intent.getIntExtra(BraintreeBrowserSwitchActivity.EXTRA_RESULT_CODE,
                            Activity.RESULT_OK);
            Intent browserIntent =
                    intent.getParcelableExtra(BraintreeBrowserSwitchActivity.EXTRA_INTENT);
            finishPayWithPayPal(fragment, browserResultCode, browserIntent);
        }
    }
}
