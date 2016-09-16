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

public class DimensionSQLViewParamRequestTransformer extends GetMapCallbackAdapter {
	public static final String PARAM_TIME_START = "timeStart";
	public static final String PARAM_TIME_END = "timeEnd";
	public static final String PARAM_ELEVATION_START = "elevationStart";
	public static final String PARAM_ELEVATION_END = "elevationEnd";
	
	private List<String> layersToMatch = null;
	private List<String> customDimensionsToTransform = null;
	private boolean transformTime = true;
	private boolean transformElevation = true;
	private String timeFormatPattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";
	private DateTimeFormatter timeFormatter = ISODateTimeFormat.dateTime();
	private String elevationFormatPattern = "%.3f";
	private CharArrayWriter elevationFormatterBuffer = new CharArrayWriter();
	private Formatter elevationFormatter = new Formatter(elevationFormatterBuffer);
	private DateTimeZone timeZone = DateTimeZone.UTC;

	
	public List<String> getLayersToMatch() {
		return layersToMatch;
	}

	public void setLayersToMatch(List<String> layersToMatch) {
		this.layersToMatch = layersToMatch;
	}

	public List<String> getCustomDimensionsToTransform() {
		return customDimensionsToTransform;
	}

	public void setCustomDimensionsToTransform(List<String> customDimensionsToTransform) {
		this.customDimensionsToTransform = customDimensionsToTransform;
	}

	public boolean transformTime() {
		return transformTime;
	}

	public void setTransformTime(boolean transformTime) {
		this.transformTime = transformTime;
	}

	public boolean transformElevation() {
		return transformElevation;
	}

	public void setTransformElevation(boolean transformElevation) {
		this.transformElevation = transformElevation;
	}
	
	public DateTimeZone getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(DateTimeZone timeZone) {
		this.timeZone = timeZone;
	}
	
	public void setTimeZoneByOffsetMillis(int millisOffset) {
		this.timeZone = DateTimeZone.forOffsetMillis(millisOffset);
	}

	public String getTimeFormatPattern() {
		return timeFormatPattern;
	}

	/**
	 * Set the date time formatter pattern for time view parameters.
	 * The pattern is be parsed using {@link org.joda.time.format.DateTimeFormat#forPattern(String)}.
	 * The default format is "yyyy-MM-dd'T'HH:mm:ss.SSSZZ"
	 * 
	 * @param pattern
	 * @throws IllegalArgumentException
	 */
	public void setTimeFormatPattern(final String pattern)  throws IllegalArgumentException {
		this.timeFormatter = DateTimeFormat.forPattern(pattern).withZone(this.timeZone);
		this.timeFormatPattern = pattern;
	}

	public String getElevationFormatPattern() {
		return elevationFormatPattern;
	}

	/**
	 * Define formatter for elevation view parameters using printf-style format
	 * (see {@link java.util.Formatter}). The default format is "%.3f".
	 * 
	 * @param pattern
	 */
	public void setElevationFormatPattern(final String pattern) {
		this.elevationFormatPattern = pattern;
		
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

	
	
	@Override
	public GetMapRequest initRequest(GetMapRequest request) {
		final List<MapLayerInfo> layers = request.getLayers();
		List<Map<String, String>> viewParams = request.getViewParams();
		Map<String, String> dimViewParams = new HashMap<String, String>();
		boolean shouldTransform = false;
		int layerCount = layers.size();
		// Logic: if layersToMatch is null (default), always transform. 
		if (this.layersToMatch == null) {
			shouldTransform = true;
		// Else if it's not empty, only transform if the request contains one of these layers.
		} else if (this.layersToMatch.size() > 0) {
			for (int i = 0; i < layerCount; i++) {
				final MapLayerInfo layer = layers.get(i);
				if (this.layersToMatch.contains(layer.getName())) {
					shouldTransform = true;
					break;
				}
			}
		}
		if (shouldTransform) {
			if (this.transformTime) {
				dimViewParams.putAll(this.getTimesAsViewParams(request.getTime()));
			}
			if (this.transformElevation) {
				dimViewParams.putAll(this.getElevationsAsViewParams(request.getElevation()));
			}
			// Logic: if customDimensionsToTransform is null (default), include all custom dims.
			if (this.customDimensionsToTransform == null) {
				for (String dimensionName: getAllCustomDimensionNames(request)) {	
					dimViewParams.putAll(this.getCustomDimensionAsViewParams(dimensionName, request.getCustomDimension(dimensionName)));
				}
			// Else if it's not empty, only include the matching custom dims
			} else if (this.customDimensionsToTransform.size() > 0) {
				for (String dimensionName:this.customDimensionsToTransform) {
					if (hasCustomDimensionSet(request,dimensionName)) {
						dimViewParams.putAll(this.getCustomDimensionAsViewParams(dimensionName, request.getCustomDimension(dimensionName)));
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
						layerParams.putAll(dimViewParams);
						viewParams.set(i, layerParams);
					}
				} else {
					//The lengths should match at this point, throw error:
					String msg = layerCount + " layers in request, but " + viewParams.size()
	                + " view params set. Cannot correctly append dimension view parameters .";
	                throw new ServiceException(msg, getClass().getName());
				}
				request.setViewParams(viewParams);
			}
		}
		return super.initRequest(request);

	}
	
	private Map<String, String> getTimesAsViewParams(List<Object> requestedTimes) {
		Map<String,String> retval = null;
		List<String> startTimes;
		List<String> endTimes;
		if (requestedTimes != null) {
			if (requestedTimes.isEmpty()) {
				return Collections.emptyMap();
			}
			retval = new HashMap<String,String>(2);
			startTimes = new ArrayList<String>();
			endTimes = new ArrayList<String>();
			String startStr;
			Date start, end;
			for (Object time:requestedTimes) {
				if (time instanceof Date) {
					startStr = getFormattedTimeValue((Date)time);
					startTimes.add(startStr);
					endTimes.add(startStr);
				} else if (time instanceof DateRange) {
					start = ((DateRange)time).getMinValue();
					end = ((DateRange)time).getMaxValue();
					startTimes.add(getFormattedTimeValue(start));
					endTimes.add(getFormattedTimeValue(end));
				}
			}
			retval.put(PARAM_TIME_START, String.join(",", startTimes));
			retval.put(PARAM_TIME_END, String.join(",", endTimes));
		}
		return retval;
	}
	
	private Map<String, String> getElevationsAsViewParams(List<Object> requestedElevations) {
		Map<String,String> retval = null;
		List<String> startElevations;
		List<String> endElevations;
		if (requestedElevations != null) {
			if (requestedElevations.isEmpty()) {
				return Collections.emptyMap();
			}
			retval = new HashMap<String,String>(2);
			startElevations = new ArrayList<String>();
			endElevations = new ArrayList<String>();
			String startStr;
			Double start, end;
			for (Object elev:requestedElevations) {
				if (elev instanceof Double) {
					startStr = getFormattedElevationValue((Double)elev);
					startElevations.add(startStr);
					endElevations.add(startStr);
				} else if (elev instanceof NumberRange) {
					start = new Double(((NumberRange<?>)elev).getMinimum());
					end = new Double(((NumberRange<?>)elev).getMaximum());
					startElevations.add(getFormattedElevationValue(start));
					endElevations.add(getFormattedElevationValue(end));
				}
			}
			retval.put(PARAM_ELEVATION_START, String.join(",", startElevations));
			retval.put(PARAM_ELEVATION_END, String.join(",", endElevations));
		}
		return retval;
	}
	
	private Map<String, String> getCustomDimensionAsViewParams(String dimensionName, List<String> requestedValues) {
		Map<String,String> retval = null;
		if (requestedValues != null) {
			if (requestedValues.isEmpty()) {
				return Collections.emptyMap();
			}
			retval = new HashMap<String, String>(1);
			//Just pass the custom dim values as-is
			retval.put("DIM_" + dimensionName, String.join(",", requestedValues));
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
			for (String name:kvp.keySet()) {
				if (name.startsWith("DIM_")) {
					retval.add(name.substring(4));
				}
			}
		}
		return retval;
	}
	
	
}
