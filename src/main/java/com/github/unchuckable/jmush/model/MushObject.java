package com.github.unchuckable.jmush.model;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class MushObject {

  private int dbRef;
  private String name;

  private EnumSet<Power> powers = EnumSet.noneOf(Power.class);
  private EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);

  private Map<String, String> attributes = new HashMap<>();

  public void setDbRef(int dbRef) {
    this.dbRef = dbRef;
  }

  public MushObject withDbRef(int dbRef) {
    setDbRef(dbRef);
    return this;
  }

  public int getDbRef() {
    return dbRef;
  }

  public void setDbRefString(String dbRef) {
    if (dbRef.charAt(0) != '#') {
      throw new IllegalArgumentException("Not a valid dbref.");
    }
    setDbRef(Integer.parseInt(dbRef.substring(1)));
  }

  public MushObject withDbRefString(String dbRef) {
    setDbRefString(dbRef);
    return this;
  }

  public String getDbRefString() {
    return "#" + dbRef;
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

  public void setFlags(EnumSet<Flag> flags) {
    this.flags = flags.clone();
  }

  public MushObject withFlag(Flag flag) {
    this.flags.add(flag);
    return this;
  }

  public EnumSet<Flag> getFlags() {
    return this.flags;
  }

  public boolean hasFlag(Flag flag) {
    return this.flags.contains(flag);
  }

  public void setPower(EnumSet<Power> powers) {
    this.powers = powers.clone();
  }

  public MushObject withPower(Power power) {
    this.powers.add(power);
    return this;
  }

  public EnumSet<Power> getPowers() {
    return this.powers;
  }

  public boolean hasPower(Power power) {
    return this.powers.contains(power);
  }

  public String getAttribute(String key) {
    return attributes.get(key);
  }

  public void setAttribute(String key, String value) {
    this.attributes.put(key, value);
  }
}
