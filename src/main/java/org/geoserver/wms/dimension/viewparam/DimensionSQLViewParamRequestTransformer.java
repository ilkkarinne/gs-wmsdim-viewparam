package org.geoserver.wms.dimension.viewparam;

import java.io.CharArrayWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.platform.ServiceException;
import org.geoserver.wms.GetMapCallbackAdapter;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.MapLayerInfo;
import org.geotools.util.DateRange;
import org.geotools.util.NumberRange;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.opengis.feature.type.Name;

public class DimensionSQLViewParamRequestTransformer extends GetMapCallbackAdapter {

    private static final Logger log = org.geotools.util.logging.Logging
            .getLogger(DimensionSQLViewParamRequestTransformer.class.getName());

    public enum DimensionName {
        TIME, ELEVATION
    }

    public enum RangeLimitType {
        START, END
    }

    private Map<DimensionName, Map<RangeLimitType, String>> viewParameterNames;

    private Map<String, String> customDimensionParameterNames;

    private List<Name> resourceNamesToMatch;

    private List<String> customDimensionsToTransform;

    private boolean transformTime;

    private boolean transformElevation;

    private String timeFormatPattern;

    private DateTimeFormatter timeFormatter;

    private String elevationFormatPattern;

    private CharArrayWriter elevationFormatterBuffer;

    private Formatter elevationFormatter;

    private DateTimeZone timeZone;

    private boolean overrideExistingViewParams;

    public DimensionSQLViewParamRequestTransformer() {
        this.viewParameterNames = new HashMap<DimensionName, Map<RangeLimitType, String>>(2);
        this.customDimensionParameterNames = new HashMap<String, String>();
        Map<RangeLimitType, String> forDim = new HashMap<RangeLimitType, String>(2);
        forDim.put(RangeLimitType.START, "timeStart");
        forDim.put(RangeLimitType.END, "timeEnd");
        this.viewParameterNames.put(DimensionName.TIME, forDim);
        forDim = new HashMap<RangeLimitType, String>(2);
        forDim.put(RangeLimitType.START, "elevationStart");
        forDim.put(RangeLimitType.END, "elevationEnd");
        this.viewParameterNames.put(DimensionName.ELEVATION, forDim);
        this.resourceNamesToMatch = null;
        this.customDimensionsToTransform = null;
        this.transformTime = true;
        this.transformElevation = true;
        this.timeFormatPattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";
        this.timeFormatter = ISODateTimeFormat.dateTime();
        this.elevationFormatPattern = "%.3f";
        this.elevationFormatterBuffer = new CharArrayWriter();
        this.elevationFormatter = new Formatter(elevationFormatterBuffer);
        this.timeZone = DateTimeZone.UTC;
        this.overrideExistingViewParams = false;
    }

    /**
     * Get the qualified names of the layer Resources in a GetMap request triggering dimension parameters to be copied as SQL View Parameters.
     * 
     * @see org.geoserver.catalog.ResourceInfo#getQualifiedName()
     * @see org.opengis.feature.type.Name
     * 
     * @return list of layer names to include, or null if any layer is matched.
     */
    public List<Name> getResourceNamesToMatch() {
        return resourceNamesToMatch;
    }

    /**
     * Set the qualified names of the layer Resources in a GetMap request triggering dimension parameters to be copied as SQL View Parameters. If set
     * to null (default) dimension parameter copying is enabled for any GetMap request regardless of the requested layer name(s).
     * 
     * @see org.geoserver.catalog.ResourceInfo#getQualifiedName()
     * @see org.opengis.feature.type.Name
     * 
     * @param layersToMatch
     */
    public void setResourceNamesToMatch(List<Name> names) {
        this.resourceNamesToMatch = names;
    }

    /**
     * Get names of the custom dimensions to transform.
     * 
     * @return list of custom dimension names to include, or null any custom dimension (DIM_ prefix) is transformed.
     */
    public List<String> getCustomDimensionsToTransform() {
        return customDimensionsToTransform;
    }

    /**
     * Set names of the custom dimensions to transform. If set to null (default) all custom dimensions included in the GetMap requests are transformed
     * into the corresponding view parameters.
     * 
     * @param customDimensionsToTransform
     */
    public void setCustomDimensionsToTransform(List<String> customDimensionsToTransform) {
        this.customDimensionsToTransform = customDimensionsToTransform;
    }

    /**
     * Is the TIME parameter transformation enabled for any requests?
     * 
     * @return true if enabled
     */
    public boolean isTransformTimeEnabled() {
        return transformTime;
    }

    /**
     * Enable or disable TIME parameter transformation. Enabled by default.
     * 
     * @param transformTime set true to enable
     */
    public void setTransformTimeEnabled(boolean transformTime) {
        this.transformTime = transformTime;
    }

    /**
     * Is the ELEVATION parameter transformation enabled for any requests?
     * 
     * @return true if enabled
     */
    public boolean isTransformElevationEnabled() {
        return transformElevation;
    }

    /**
     * Enable or disable ELEVATION parameter transformation. Enabled by default.
     * 
     * @param transformElevation set true to enable
     */
    public void setTransformElevationEnabled(boolean transformElevation) {
        this.transformElevation = transformElevation;
    }

    /**
     * Returns the time zone used for encoding the time dimension values as SQL View parameters.
     * 
     * @return
     */
    public DateTimeZone getTimeZone() {
        return timeZone;
    }

    /**
     * Set the time zone used for encoding the time dimension values as SQL View parameters.
     * 
     * @param timeZone
     */
    public void setTimeZone(DateTimeZone timeZone) {
        this.timeZone = timeZone;
    }

    /**
     * Set the time zone offset used for encoding the time dimension values as SQL View parameters.
     * 
     * @param millisOffset
     */
    public void setTimeZoneByOffsetMillis(int millisOffset) {
        this.timeZone = DateTimeZone.forOffsetMillis(millisOffset);
    }

    /**
     * Set the time zone used for encoding the time dimension values as SQL View parameters using a time zone id. Accepts the long format time zone
     * ids as returned by {@link DateTimeZone#getAvailableIDs()}
     * 
     * @param millisOffset
     */
    public void setTimeZoneById(String longTimeZoneId) {
        this.timeZone = DateTimeZone.forID(longTimeZoneId);
    }

    /**
     * Returns the current pattern for formatting time valued SQL View Parameters.
     *
     * @return the format
     * @see org.joda.time.format.DateTimeFormat
     */
    public String getTimeFormatPattern() {
        return timeFormatPattern;
    }

    /**
     * Set the date time formatter pattern for time view parameters. The pattern is be parsed using
     * {@link org.joda.time.format.DateTimeFormat#forPattern(String)}. The default format is "yyyy-MM-dd'T'HH:mm:ss.SSSZZ"
     * 
     * @param pattern
     * @throws IllegalArgumentException
     */
    public void setTimeFormatPattern(final String pattern) throws IllegalArgumentException {
        this.timeFormatter = DateTimeFormat.forPattern(pattern).withZone(this.timeZone);
        this.timeFormatPattern = pattern;
    }

    /**
     * Returns the current pattern for formatting elevation valued SQL View Parameters.
     * 
     * @return
     */
    public String getElevationFormatPattern() {
        return elevationFormatPattern;
    }

    /**
     * Define formatter for elevation view parameters using printf-style format (see {@link java.util.Formatter}). The default format is "%.3f".
     * 
     * @param pattern
     */
    public void setElevationFormatPattern(final String pattern) {
        this.elevationFormatPattern = pattern;

    }
    public Map<DimensionName, Map<RangeLimitType, String>> getViewParameterNames() {
        return viewParameterNames;
    }

    public void setViewParameterNames(
            Map<DimensionName, Map<RangeLimitType, String>> viewParameterNames) {
        this.viewParameterNames = viewParameterNames;
    }
    
    public void setViewParameterName(DimensionName dimension, RangeLimitType type,
            String paramName) {
        if (!this.viewParameterNames.containsKey(dimension)) {
            this.viewParameterNames.put(dimension, new HashMap<RangeLimitType, String>());
        }
        this.viewParameterNames.get(dimension).put(type, paramName);
    }

    public String getViewParameterName(DimensionName dimension, RangeLimitType type) {
        if (this.viewParameterNames.containsKey(dimension)) {
            return this.viewParameterNames.get(dimension).get(type);
        } else {
            return null;
        }
    }

    public Map<String, String> getCustomDimensionParameterNames() {
        return customDimensionParameterNames;
    }

    public void setCustomDimensionParameterNames(Map<String, String> customDimensionParameterNames) {
        this.customDimensionParameterNames = customDimensionParameterNames;
    }
    
    public void setCustomDimensionViewParameterName(String customDimensionName,
            String viewParameterName) {
        this.customDimensionParameterNames.put(customDimensionName, viewParameterName);
    }

    public String getCustomDimensionViewParameterName(String dimensionName) {
        if (this.customDimensionParameterNames.containsKey(dimensionName)) {
            return this.customDimensionParameterNames.get(dimensionName);
        } else {
            return dimensionName;
        }
    }

    public boolean isOverrideExistingViewParams() {
        return overrideExistingViewParams;
    }

    public void setOverrideExistingViewParams(boolean overrideExistingViewParams) {
        this.overrideExistingViewParams = overrideExistingViewParams;
    }

   

  

    @Override
    public GetMapRequest initRequest(GetMapRequest request) {

        List<MapLayerInfo> l = request.getLayers();
        if (l != null && !l.isEmpty()) {
            List<LayerInfo> layers = new ArrayList<LayerInfo>(l.size());
            for (MapLayerInfo i : l) {
                layers.add(i.getLayerInfo());
            }
            List<Map<String, String>> viewParams = request.getViewParams();
            Map<String, String> dimViewParams = new HashMap<String, String>();
            boolean shouldTransform = false;
            int layerCount = layers.size();
            // Logic: if resourceNameToMatch is null (default), always transform.
            if (this.resourceNamesToMatch == null) {
                shouldTransform = true;
                log.log(Level.FINE, "Null layers to match, transform dims for any GetMap request");
                // Else if it's not empty, only transform if the request contains one of these layers.
            } else if (this.resourceNamesToMatch.size() > 0) {
                for (int i = 0; i < layerCount; i++) {
                    final LayerInfo layer = layers.get(i);
                    if (this.resourceNamesToMatch
                            .contains(layer.getResource().getQualifiedName())) {
                        shouldTransform = true;
                        log.log(Level.FINE, "Found triggering layer '" + layer.getName()
                                + "' in GetMap request, enabling dim transformation");
                        break;
                    }
                }
            }
            if (shouldTransform) {
                if (this.transformTime) {
                    log.log(Level.FINEST, "Time dimension transformation enabled");
                    addToViewParams(this.getTimesAsViewParams(request.getTime()), dimViewParams);
                }
                if (this.transformElevation) {
                    log.log(Level.FINEST, "Elevation dimension transformation enabled");
                    addToViewParams(this.getElevationsAsViewParams(request.getElevation()),
                            dimViewParams);
                }
                // Logic: if customDimensionsToTransform is null (default), include all custom dims.
                if (this.customDimensionsToTransform == null) {
                    log.log(Level.FINEST,
                            "Null custom dims to match given, transforming any custom dimension");
                    for (String dimensionName : getAllCustomDimensionNames(request)) {
                        addToViewParams(this.getCustomDimensionAsViewParams(dimensionName,
                                request.getCustomDimension(dimensionName)), dimViewParams);
                    }
                    // Else if it's not empty, only include the matching custom dims
                } else if (this.customDimensionsToTransform.size() > 0) {
                    for (String dimensionName : this.customDimensionsToTransform) {
                        if (hasCustomDimensionSet(request, dimensionName)) {
                            log.log(Level.FINE, "Found matching custom dimension '" + dimensionName
                                    + "', transforming");
                            addToViewParams(
                                    this.getCustomDimensionAsViewParams(dimensionName,
                                            request.getCustomDimension(dimensionName)),
                                    dimViewParams);
                        } else {
                            log.log(Level.FINEST, "Skipping transformation for custom dimension '"
                                    + dimensionName + "'");
                        }
                    }
                }
                if (!dimViewParams.isEmpty()) {
                    if (viewParams == null) {
                        viewParams = new ArrayList<Map<String, String>>(layerCount);
                        for (int i = 0; i < layerCount; i++) {
                            viewParams.add(dimViewParams);
                        }
                    } else if (viewParams.size() == layerCount) {
                        Map<String, String> layerParams;
                        for (int i = 0; i < layerCount; i++) {
                            layerParams = viewParams.get(i);
                            addToViewParams(dimViewParams, layerParams);
                            viewParams.set(i, layerParams);
                        }
                    } else {
                        // The lengths should match at this point, throw error if not:
                        String msg = layerCount + " layers in request, but " + viewParams.size()
                                + " view params set. Cannot correctly append dimension view parameters .";
                        throw new ServiceException(msg, getClass().getName());
                    }
                    request.setViewParams(viewParams);
                    if (log.isLoggable(Level.FINE)) {
                        logViewParams(request, viewParams);
                    }

                }
            } else {
                log.log(Level.FINEST, "Not transforming dimension parameters");
            }
        }
        return super.initRequest(request);

    }

    private void addToViewParams(Map<String, String> from, Map<String, String> to) {
        if (from != null && to != null && !from.isEmpty()) {
            if (this.overrideExistingViewParams) {
                to.putAll(from);
            } else {
                for (String name : from.keySet()) {
                    if (!to.containsKey(name)) {
                        to.put(name, from.get(name));
                    }
                }
            }
        }
    }

    private String getFormattedElevationValue(Double elevation) throws IllegalFormatException {
        String retval = null;
        if (this.elevationFormatPattern != null) {
            this.elevationFormatterBuffer.reset();
            this.elevationFormatter.format(this.elevationFormatPattern, elevation);
            retval = elevationFormatterBuffer.toString();
        }
        return retval;
    }

    private String getFormattedTimeValue(Date dateTime) throws IllegalFormatException {
        return this.timeFormatter.print(dateTime.getTime());
    }

    private Map<String, String> getTimesAsViewParams(List<Object> requestedTimes) {
        Map<String, String> retval = null;
        List<String> startTimes;
        List<String> endTimes;
        if (requestedTimes != null) {
            if (requestedTimes.isEmpty()) {
                return Collections.emptyMap();
            }
            retval = new HashMap<String, String>(2);
            startTimes = new ArrayList<String>();
            endTimes = new ArrayList<String>();
            String startStr;
            Date start, end;
            for (Object time : requestedTimes) {
                if (time instanceof Date) {
                    startStr = getFormattedTimeValue((Date) time);
                    startTimes.add(startStr);
                    endTimes.add(startStr);
                } else if (time instanceof DateRange) {
                    start = ((DateRange) time).getMinValue();
                    end = ((DateRange) time).getMaxValue();
                    startTimes.add(getFormattedTimeValue(start));
                    endTimes.add(getFormattedTimeValue(end));
                }
            }

            if (this.viewParameterNames.containsKey(DimensionName.TIME)) {
                String startParam = this.getViewParameterName(DimensionName.TIME,
                        RangeLimitType.START);
                String endParam = this.getViewParameterName(DimensionName.TIME, RangeLimitType.END);

                // In Java 8 this can be done using String.join(delimiter,collection):
                if (startParam != null) {
                    retval.put(startParam, StringUtils.join(startTimes, ','));
                }
                if (endParam != null) {
                    retval.put(endParam, StringUtils.join(endTimes, ','));
                }
            }
        }
        return retval;
    }

    private Map<String, String> getElevationsAsViewParams(List<Object> requestedElevations) {
        Map<String, String> retval = null;
        List<String> startElevations;
        List<String> endElevations;
        if (requestedElevations != null) {
            if (requestedElevations.isEmpty()) {
                return Collections.emptyMap();
            }
            retval = new HashMap<String, String>(2);
            startElevations = new ArrayList<String>();
            endElevations = new ArrayList<String>();
            String startStr;
            Double start, end;
            for (Object elev : requestedElevations) {
                if (elev instanceof Double) {
                    startStr = getFormattedElevationValue((Double) elev);
                    startElevations.add(startStr);
                    endElevations.add(startStr);
                } else if (elev instanceof NumberRange) {
                    start = new Double(((NumberRange<?>) elev).getMinimum());
                    end = new Double(((NumberRange<?>) elev).getMaximum());
                    startElevations.add(getFormattedElevationValue(start));
                    endElevations.add(getFormattedElevationValue(end));
                }
            }
            if (this.viewParameterNames.containsKey(DimensionName.ELEVATION)) {
                String startParam = this.getViewParameterName(DimensionName.ELEVATION,
                        RangeLimitType.START);
                String endParam = this.getViewParameterName(DimensionName.ELEVATION,
                        RangeLimitType.END);

                // In Java 8 this can be done using String.join(delimiter,collection):
                if (startParam != null) {
                    retval.put(startParam, StringUtils.join(startElevations, ','));
                }
                if (endParam != null) {
                    retval.put(endParam, StringUtils.join(endElevations, ','));
                }
            }
        }
        return retval;
    }

    private Map<String, String> getCustomDimensionAsViewParams(String dimensionName,
            List<String> requestedValues) {
        Map<String, String> retval = null;
        if (requestedValues != null) {
            if (requestedValues.isEmpty()) {
                return Collections.emptyMap();
            }
            retval = new HashMap<String, String>(1);
            String viewParamName = this.getCustomDimensionViewParameterName(dimensionName);
            // In Java 8 this can be done using String.join(delimiter,collection):
            retval.put("DIM_" + viewParamName, StringUtils.join(requestedValues, ','));
        }
        return retval;
    }

    private static boolean hasCustomDimensionSet(GetMapRequest request, String dimensionName) {
        boolean retval = false;
        if (request.getRawKvp() != null) {
            String key = "DIM_" + dimensionName;
            String value = request.getRawKvp().get(key);
            if (value != null) {
                retval = true;
            }
        }
        return retval;
    }

    private static List<String> getAllCustomDimensionNames(GetMapRequest request) {
        List<String> retval = null;
        Map<String, String> kvp = request.getRawKvp();
        if (kvp != null) {
            retval = new ArrayList<String>();
            for (String name : kvp.keySet()) {
                if (name.startsWith("DIM_")) {
                    retval.add(name.substring(4));
                }
            }
        }
        return retval;
    }

    private static void logViewParams(GetMapRequest req, List<Map<String, String>> params) {
        if (params != null) {
            List<MapLayerInfo> layers = req.getLayers();
            if (layers != null) {
                if (layers.size() != params.size()) {
                    log.severe("Mismatch between layer list and viewparam list sizes!");
                    return;
                }
                Map<String, String> layerParams;
                log.log(Level.FINE,
                        "SQL View Parameters by layer after dimension transformation follow");
                for (int i = 0; i < layers.size(); i++) {
                    layerParams = params.get(i);
                    log.log(Level.FINE, layers.get(i).getName());
                    for (String name : layerParams.keySet()) {
                        log.log(Level.FINE, "\t" + name + "=" + layerParams.get(name));
                    }
                }
            }
        }
    }
}
