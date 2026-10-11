package com.android.billingclient.api;
public final class BillingClient {
 private static final int UNAVAILABLE = 3;
 public static Builder newBuilder(android.content.Context c){return new Builder();}
 public static final class Builder { public Builder setListener(PurchasesUpdatedListener l){return this;} public Builder enablePendingPurchases(PendingPurchasesParams p){return this;} public BillingClient build(){return new BillingClient();} }
 public static final class ProductType { public static final String SUBS="subs", INAPP="inapp"; }
 public static final class BillingResponseCode { public static final int OK=0, USER_CANCELED=1, SERVICE_UNAVAILABLE=2, BILLING_UNAVAILABLE=3, ITEM_ALREADY_OWNED=7; }
 public boolean isReady(){return false;}
 public void startConnection(BillingClientStateListener l){ l.onBillingSetupFinished(new BillingResult(UNAVAILABLE)); }
 public void queryProductDetailsAsync(QueryProductDetailsParams p, ProductDetailsResponseListener l){ l.onProductDetailsResponse(new BillingResult(UNAVAILABLE), java.util.Collections.emptyList()); }
 public void queryPurchasesAsync(QueryPurchasesParams p, PurchasesResponseListener l){ l.onQueryPurchasesResponse(new BillingResult(UNAVAILABLE), java.util.Collections.emptyList()); }
 public BillingResult launchBillingFlow(android.app.Activity a, BillingFlowParams p){ return new BillingResult(UNAVAILABLE); }
 public void acknowledgePurchase(AcknowledgePurchaseParams p, AcknowledgePurchaseResponseListener l){ l.onAcknowledgePurchaseResponse(new BillingResult(UNAVAILABLE)); }
}
