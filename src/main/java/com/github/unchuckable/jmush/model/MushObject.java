package com.github.unchuckable.jmush.model;

import java.util.HashMap;
import java.util.Map;

public class MushObject {

  private String dbRef;
  private String name;

  private Map<String,String> attributes = new HashMap<>();
  
  public void setDbRef(String dbRef) {
    this.dbRef = dbRef;
  }

  public MushObject withDbRef(String dbRef) {
    setDbRef(dbRef);
    return this;
  }

  public String getDbRef() {
    return dbRef;
  }

  public void setName(String name) {
    this.name = name;
  }

  public MushObject withName(String name) {
    setName(name);
    return this;
  }

  public String getName() {
    return this.name;
  }

  public String getAttribute(String key) {
    return attributes.get(key);
  }

  public void setAttribute(String key, String value) {
    this.attributes.put(key, value);
  }
}
