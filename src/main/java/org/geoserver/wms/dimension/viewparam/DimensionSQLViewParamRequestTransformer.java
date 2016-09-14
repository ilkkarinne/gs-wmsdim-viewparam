package org.geoserver.wms.dimension.viewparam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geoserver.platform.ServiceException;
import org.geoserver.wms.GetMapCallbackAdapter;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.MapLayerInfo;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class DimensionSQLViewParamRequestTransformer extends GetMapCallbackAdapter {
	

	private List<String> layersToMatch = new ArrayList<String>();
	private List<String> customDimensionsToTransform = new ArrayList<String>();
	private boolean transformTime = true;
	private boolean transformElevation = true;
	
	//TODO: customizable time formatting
	private DateTimeFormatter timeFormatter = DateTimeFormat.forPattern("yyyyMMdd");
	
	
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

	
	
	@Override
	public GetMapRequest initRequest(GetMapRequest request) {
		final List<MapLayerInfo> layers = request.getLayers();
		List<Map<String, String>> viewParams = request.getViewParams();
		Map<String, String> dimViewParams = new HashMap<String, String>();
		boolean shouldTransform = false;
		int layerCount = layers.size();
		
		for (int i = 0; i < layerCount; i++) {
			final MapLayerInfo layer = layers.get(i);
			if (this.layersToMatch.contains(layer.getName())) {
				shouldTransform = true;
				break;
			}
		}
		if (shouldTransform) {
			if (this.transformTime) {
				dimViewParams = this.combineViewParams(this.getTimeViewParams(request.getTime()), dimViewParams);
			}
			if (this.transformElevation) {
				dimViewParams = this.combineViewParams(this.getElevationViewParams(request.getElevation()), dimViewParams);
			}
			for (String dimensionName:this.customDimensionsToTransform) {
				if (this.hasCustomDimension(request,dimensionName)) {
					dimViewParams = this.combineViewParams(this.getCustomDimensionViewParams(dimensionName, request.getCustomDimension(dimensionName)), dimViewParams);
				}
			}
			if (!dimViewParams.isEmpty()) {
				if (viewParams == null) {
					viewParams = new ArrayList<Map<String, String>>(layerCount);
					for (int i = 0; i < layerCount; i++) {
						viewParams.add(dimViewParams);
					}
				} else if (viewParams.size() == layerCount) {
					for (int i = 0; i < layerCount; i++) {
						viewParams.set(i, this.combineViewParams(dimViewParams, viewParams.get(i)));
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
	
	private Map<String, String> getTimeViewParams(List<Object> requestedTimes) {
		Map<String,String> retval = null;
		
		if (requestedTimes != null) {
			retval = new HashMap<String,String>();
			
		}
		return retval;
	}
	
	private Map<String, String> getElevationViewParams(List<Object> requestedElevations) {
		Map<String,String> retval = new HashMap<String,String>();
		
		return retval;
	}
	
	private Map<String, String> getCustomDimensionViewParams(String dimensionName, List<String> requestedValues) {
		Map<String,String> retval = new HashMap<String,String>();
		
		return retval;
	}
	
	private boolean hasCustomDimension(GetMapRequest request, String dimensionName) {
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
	
	private Map<String, String> combineViewParams(Map<String, String> from, Map<String, String> to) {
		//TODO: add combining logic
		//If "to" == null and from != null, create new list
		return to;
	}

	
}
