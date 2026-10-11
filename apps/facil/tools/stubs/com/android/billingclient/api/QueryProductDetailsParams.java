package com.android.billingclient.api;
public final class QueryProductDetailsParams { public static Builder newBuilder(){return new Builder();}
 public static final class Builder { public Builder setProductList(java.util.List<Product> l){return this;} public QueryProductDetailsParams build(){return new QueryProductDetailsParams();} }
 public static final class Product { public static Builder newBuilder(){return new Builder();}
  public static final class Builder { public Builder setProductId(String i){return this;} public Builder setProductType(String t){return this;} public Product build(){return new Product();} } } }
