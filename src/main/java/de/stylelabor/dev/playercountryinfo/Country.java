package de.stylelabor.dev.playercountryinfo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Country {
    @JsonProperty("Name")
    private String name;
    @JsonProperty("Code")
    private String code;

    // getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}