package com.android.billingclient.api;
public final class BillingFlowParams { public static Builder newBuilder(){return new Builder();}
 public static final class Builder { public Builder setProductDetailsParamsList(java.util.List<ProductDetailsParams> l){return this;} public BillingFlowParams build(){return new BillingFlowParams();} }
 public static final class ProductDetailsParams { public static Builder newBuilder(){return new Builder();}
  public static final class Builder { public Builder setProductDetails(ProductDetails d){return this;} public Builder setOfferToken(String t){return this;} public ProductDetailsParams build(){return new ProductDetailsParams();} } } }
