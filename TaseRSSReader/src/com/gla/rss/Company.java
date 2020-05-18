package com.gla.rss;

public class Company {
    private String Symbol = "";
    private String Name = "";
    private String FullName = "";
    private int marketCap;

    public int getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(int marketCap) {
        this.marketCap = marketCap;
    }

    @Override
    public String toString() {
        return "Company{" +
                "Symbol='" + Symbol + '\'' +
                ", Name='" + Name + '\'' +
                ", FullName='" + FullName + '\'' +
                ", marketCap=" + marketCap +
                '}';
    }

    public String getSymbol() {
        return Symbol;
    }

    public void setSymbol(String symbol) {
        Symbol = symbol;
    }

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    public String getFullName() {
        return FullName;
    }

    public void setFullName(String fullName) {
        FullName = fullName;
    }
}
