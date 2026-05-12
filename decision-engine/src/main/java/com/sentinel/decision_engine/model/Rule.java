package com.sentinel.decision_engine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Rule {
    private String id;
    private String name;
    private String field;
    private String operator;
    private double value;
    private String action;

    @JsonProperty("add_risk")
    private int addRisk;
}