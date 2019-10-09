package com.github.unchuckable.jmush.mushcode;

public class Value {
  
  private String value;
  
  public static Value of(String value) {
    return new Value(value);
  }
  
  private Value( String value ) {
    this.value = value;
  }
  
  public String asString() {
    return value;
  }
  
  @Override
  public String toString() {
    return value;
  }
  
}
