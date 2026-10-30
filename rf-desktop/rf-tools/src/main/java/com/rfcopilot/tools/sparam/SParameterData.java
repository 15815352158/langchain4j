package com.rfcopilot.tools.sparam;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Touchstone 解析结果：频率点（统一为 Hz）与各 S 参数的幅度(dB)/相位(度)。
 */
public class SParameterData {

    private final double[] freqHz;
    private final Map<String, double[]> dbValues = new LinkedHashMap<>();
    private final Map<String, double[]> phaseValues = new LinkedHashMap<>();
    private final int ports;
    private final double referenceImpedance;

    public SParameterData(double[] freqHz, int ports, double referenceImpedance) {
        this.freqHz = freqHz;
        this.ports = ports;
        this.referenceImpedance = referenceImpedance;
    }

    public void put(String param, double[] db, double[] phase) {
        dbValues.put(param, db);
        phaseValues.put(param, phase);
    }

    public double[] freqHz() {
        return freqHz;
    }

    public Set<String> params() {
        return dbValues.keySet();
    }

    public double[] db(String param) {
        return dbValues.get(param);
    }

    public double[] phase(String param) {
        return phaseValues.get(param);
    }

    public int ports() {
        return ports;
    }

    public double referenceImpedance() {
        return referenceImpedance;
    }

    public double startFreqHz() {
        return freqHz.length == 0 ? 0 : freqHz[0];
    }

    public double stopFreqHz() {
        return freqHz.length == 0 ? 0 : freqHz[freqHz.length - 1];
    }

    public int pointCount() {
        return freqHz.length;
    }
}
