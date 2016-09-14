package org.geoserver.wms.dimension.viewparam;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.util.Map;

import org.geoserver.platform.ServiceException;
import org.geoserver.wms.GetMap;
import org.geoserver.wms.GetMapOutputFormat;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.WMS;
import org.geoserver.wms.WMSMapContent;
import org.geoserver.wms.WMSMockData;
import org.geoserver.wms.WMSTestSupport;
import org.geoserver.wms.WebMap;
import org.geoserver.wms.map.RenderedImageMap;
import org.geotools.util.DateRange;
import org.geotools.util.NumberRange;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.geoserver.wms.WMSMockData.DummyRasterMapProducer;
import org.junit.Before;
import org.junit.Test;

import com.vividsolutions.jts.geom.Point;


public class DimensionViewParamTransformerTest extends WMSTestSupport {
	private WMSMockData mockData;

	private GetMapRequest request;
	 
	private GetMap getMapOp;
	
	private DimensionSQLViewParamRequestTransformer transformer;
	
	@Before
    public void setUp() throws Exception {
        transformer = applicationContext.getBean("getMapCallback", org.geoserver.wms.dimension.viewparam.DimensionSQLViewParamRequestTransformer.class);
		transformer.setLayersToMatch(Arrays.asList("geos:layerOne"));
	    transformer.setTransformTime(true);
	    transformer.setTransformElevation(true);
	    transformer.setCustomDimensionsToTransform(Arrays.asList("TESTDIM"));
	    transformer.setElevationFormatPattern("%.3f");
	    transformer.setTimeFormatPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");
	    
        mockData = new WMSMockData();
        mockData.setUp();
        request = mockData.createRequest();
        request.setFormat(DummyRasterMapProducer.MIME_TYPE);

        request.setLayers(Arrays.asList(
        		mockData.addFeatureTypeLayer("layerOne", Point.class),
        		mockData.addFeatureTypeLayer("layerTwo", Point.class)));
        request.setStyles(Arrays.asList(
        		mockData.getDefaultStyle().getStyle(),
        		mockData.getDefaultStyle().getStyle()));
        final DummyRasterMapProducer producer = new DummyRasterMapProducer(){
        	/* A bit of hacking required here due to a class cast problem in org.geoserver.wms.getMap row 223:
        
        		for (Object currentTime : times) {
                map = executeInternal(mapContent, request, delegate, Arrays.asList(currentTime), elevations);
                
                // remove layers to start over again
                mapContent.layers().clear();
                
                // collect the layer
                images.add(((RenderedImageMap)map).getImage());
                            ^^^^^^^^^^^^^^^^^
            }
        	*/
			@Override
			public WebMap produceMap(WMSMapContent mapContent) throws ServiceException, IOException {
				 produceMapCalled = true;
				 RenderedImage i = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
		         return new RenderedImageMap(mapContent, i, DummyRasterMapProducer.MIME_TYPE) {
		         };
			}
        	
        };
        final WMS wms = new WMS(mockData.getGeoServer()) {
            @Override
            public GetMapOutputFormat getMapOutputFormat(final String mimeType) {
                if (DummyRasterMapProducer.MIME_TYPE.equals(mimeType)) {
                    return producer;
                }
                return null;
            }
        };
        getMapOp = new GetMap(wms);
    }

	
	@Test
	public void testSingleTimeTransformation() throws Exception {
		String timeStr = "2004-12-13T21:00:00.000Z";
		request.setTime(Arrays.asList((new DateTime(timeStr).toDate())));
		WebMap map = null;
		DateTimeFormatter formatter = DateTimeFormat.forPattern(transformer.getTimeFormatPattern());
		String timeFormatted = formatter.parseDateTime(timeStr).toString(formatter);
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "timeStart", timeFormatted);
			assertViewParamSet(request, "timeEnd", timeFormatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	@Test
	public void testMultipleTimeInstancesTransformation() throws Exception {
		String timeStr1 = "2004-12-13T21:00:00.000Z";
		String timeStr2 = "2004-12-13T22:00:00.000Z";
		request.setTime(Arrays.asList((new DateTime(timeStr1).toDate()),(new DateTime(timeStr2).toDate())));
		WebMap map = null;
		DateTimeFormatter formatter = DateTimeFormat.forPattern(transformer.getTimeFormatPattern());
		String time1Formatted = formatter.parseDateTime(timeStr1).toString(formatter);
		String time2Formatted = formatter.parseDateTime(timeStr2).toString(formatter);
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "timeStart", time1Formatted + "," + time2Formatted);
			assertViewParamSet(request, "timeEnd", time1Formatted + "," + time2Formatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	@Test
	public void testSingleTimeRangeTransformation() throws Exception {
		String timeBeginStr = "2004-12-13T21:00:00.000Z";
		String timeEndStr = "2004-12-14T00:00:00.000Z";
		
		request.setTime(Arrays.asList(
				new DateRange(
						(new DateTime(timeBeginStr)).toDate(), 
						(new DateTime(timeEndStr)).toDate()
				)
		));
		WebMap map = null;
		DateTimeFormatter formatter = DateTimeFormat.forPattern(transformer.getTimeFormatPattern());
		String timeBeginFormatted = formatter.parseDateTime(timeBeginStr).toString(formatter);
		String timeEndFormatted = formatter.parseDateTime(timeEndStr).toString(formatter);
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "timeStart", timeBeginFormatted);
			assertViewParamSet(request, "timeEnd", timeEndFormatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	@Test
	public void testMultipleTimeRangesTransformation() throws Exception {
		String timeBeginStr1 = "2004-12-13T21:00:00.000Z";
		String timeEndStr1 = "2004-12-14T00:00:00.000Z";
		String timeBeginStr2 = "2004-12-13T21:00:00.000Z";
		String timeEndStr2 = "2004-12-14T00:00:00.000Z";
		
		request.setTime(Arrays.asList(
				new DateRange(
						(new DateTime(timeBeginStr1)).toDate(), 
						(new DateTime(timeEndStr1)).toDate()
				),
				new DateRange(
						(new DateTime(timeBeginStr1)).toDate(), 
						(new DateTime(timeEndStr1)).toDate()
				)
		));
		WebMap map = null;
		DateTimeFormatter formatter = DateTimeFormat.forPattern(transformer.getTimeFormatPattern());
		String timeBegin1Formatted = formatter.parseDateTime(timeBeginStr1).toString(formatter);
		String timeEnd1Formatted = formatter.parseDateTime(timeEndStr1).toString(formatter);
		String timeBegin2Formatted = formatter.parseDateTime(timeBeginStr2).toString(formatter);
		String timeEnd2Formatted = formatter.parseDateTime(timeEndStr2).toString(formatter);
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "timeStart", timeBegin1Formatted + "," + timeBegin2Formatted);
			assertViewParamSet(request, "timeEnd", timeEnd1Formatted + "," + timeEnd2Formatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	@Test
	public void testSingleElevationTransformation() throws Exception {
		String elevStr = "1000";
		request.setElevation(Arrays.asList(new Double(elevStr)));
		WebMap map = null;
		String elevFormatted = String.format(transformer.getElevationFormatPattern(), Double.parseDouble(elevStr));
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "elevationStart", elevFormatted);
			assertViewParamSet(request, "elevationEnd", elevFormatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	@Test
	public void testMultipleElevationInstancesTransformation() throws Exception {
		String elevStr1 = "1000";
		String elevStr2 = "2000";
		request.setElevation(Arrays.asList(
				new Double(elevStr1),
				new Double(elevStr2)
		));
		WebMap map = null;
		String elev1Formatted = String.format(transformer.getElevationFormatPattern(), Double.parseDouble(elevStr1));
		String elev2Formatted = String.format(transformer.getElevationFormatPattern(), Double.parseDouble(elevStr2));
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "elevationStart", elev1Formatted + "," + elev2Formatted);
			assertViewParamSet(request, "elevationEnd", elev1Formatted + "," + elev2Formatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	@Test
	public void testSingleElevationRangeTransformation() throws Exception {
		String elevStartStr = "1000";
		String elevEndStr = "2000";
		request.setElevation(Arrays.asList(
				new NumberRange<Double>(
						Double.class,
						new Double(elevStartStr),
						new Double(elevEndStr)
				)
		));
		WebMap map = null;
		String elevStartFormatted = String.format(transformer.getElevationFormatPattern(), Double.parseDouble(elevStartStr));
		String elevEndFormatted = String.format(transformer.getElevationFormatPattern(), Double.parseDouble(elevEndStr));
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "elevationStart", elevStartFormatted);
			assertViewParamSet(request, "elevationEnd", elevEndFormatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	@Test
	public void testMultipleElevationRangesTransformation() throws Exception {
		String elevStartStr1 = "1000";
		String elevEndStr1 = "2000";
		String elevStartStr2 = "5000";
		String elevEndStr2 = "5500";
		request.setElevation(Arrays.asList(
				new NumberRange<Double>(
						Double.class,
						new Double(elevStartStr1),
						new Double(elevEndStr1)
				),
				new NumberRange<Double>(
						Double.class,
						new Double(elevStartStr2),
						new Double(elevEndStr2)
				)
		));
		WebMap map = null;
		String elevStart1Formatted = String.format(transformer.getElevationFormatPattern(), Double.parseDouble(elevStartStr1));
		String elevStart2Formatted = String.format(transformer.getElevationFormatPattern(), Double.parseDouble(elevStartStr2));
		String elevEnd1Formatted = String.format(transformer.getElevationFormatPattern(), Double.parseDouble(elevEndStr1));
		String elevEnd2Formatted = String.format(transformer.getElevationFormatPattern(), Double.parseDouble(elevEndStr2));
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "elevationStart", elevStart1Formatted + "," + elevStart2Formatted);
			assertViewParamSet(request, "elevationEnd", elevEnd1Formatted + "," + elevEnd2Formatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	@Test
	public void testCustomDimensionTransformation() throws Exception {
		String dimValue = "100,256,ABC";
		setCustomDimensionValue(request, "testdim", dimValue);
		WebMap map = null;
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "DIM_testdim", dimValue);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	@Test
	public void testCustomDimensionMatching() throws Exception {
		String dimValue = "100,256,ABC";
		WebMap map = null;
		
		setCustomDimensionValue(request, "testdim", dimValue);
		transformer.setCustomDimensionsToTransform(Arrays.asList("testdim"));
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "DIM_testdim", dimValue);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
		
		removeCustomDimension(request, "testdim");
		try {
			map = getMapOp.run(request);
			assertViewParamNotSet(request, "DIM_testdim");
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
		
		setCustomDimensionValue(request, "testdim", dimValue);
		transformer.setCustomDimensionsToTransform(Arrays.asList("anotherdim"));
		try {
			map = getMapOp.run(request);
			assertViewParamNotSet(request, "DIM_testdim");
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
		
		setCustomDimensionValue(request, "anotherdim", dimValue);
		try {
			map = getMapOp.run(request);
			assertViewParamNotSet(request, "DIM_testdim");
			assertViewParamSet(request, "DIM_anotherdim", dimValue);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
		
		transformer.setCustomDimensionsToTransform(Arrays.asList("testdim","anotherdim"));
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "DIM_testdim", dimValue);
			assertViewParamSet(request, "DIM_anotherdim", dimValue);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	
	@Test
	public void testLayerMatching() throws Exception {
		transformer.setLayersToMatch(Arrays.asList("geos:nonExistentLayer"));
		
		String timeStr = "2004-12-13T21:00:00.000Z";
		request.setTime(Arrays.asList((new DateTime(timeStr).toDate())));
		WebMap map = null;
		DateTimeFormatter formatter = DateTimeFormat.forPattern(transformer.getTimeFormatPattern());
		String timeFormatted = formatter.parseDateTime(timeStr).toString(formatter);
		try {
			map = getMapOp.run(request);
			assertViewParamNotSet(request, "timeStart");
			assertViewParamNotSet(request, "timeEnd");
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
		request.setViewParams(null);
		transformer.setLayersToMatch(Arrays.asList("geos:layerOne"));
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "timeStart", timeFormatted);
			assertViewParamSet(request, "timeEnd", timeFormatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
		request.setViewParams(null);
		transformer.setLayersToMatch(null);
		try {
			map = getMapOp.run(request);
			assertViewParamNotSet(request, "timeStart");
			assertViewParamNotSet(request, "timeEnd");
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
		
	}
	
	@Test
	public void testEnableTime() throws Exception {
		transformer.setTransformTime(false);
		
		String timeStr = "2004-12-13T21:00:00.000Z";
		request.setTime(Arrays.asList((new DateTime(timeStr).toDate())));
		WebMap map = null;
		DateTimeFormatter formatter = DateTimeFormat.forPattern(transformer.getTimeFormatPattern());
		String timeFormatted = formatter.parseDateTime(timeStr).toString(formatter);
		try {
			map = getMapOp.run(request);
			assertViewParamNotSet(request, "timeStart");
			assertViewParamNotSet(request, "timeEnd");
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
		
		transformer.setTransformTime(true);
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "timeStart", timeFormatted);
			assertViewParamSet(request, "timeEnd", timeFormatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	@Test
	public void testEnableElevation() throws Exception {
		
		transformer.setTransformElevation(false);
		String elevStr = "1000";
		request.setElevation(Arrays.asList(new Double(elevStr)));
		WebMap map = null;
		String elevFormatted = String.format(transformer.getElevationFormatPattern(), Double.parseDouble(elevStr));
		try {
			map = getMapOp.run(request);
			assertViewParamNotSet(request, "elevationStart");
			assertViewParamNotSet(request, "elevationEnd");
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
		
		transformer.setTransformElevation(true);
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "elevationStart", elevFormatted);
			assertViewParamSet(request, "elevationEnd", elevFormatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	
	@Test
	public void testTimeFormatting() throws Exception {
		String timeStr = "2004-12-13T21:00:00.000Z";
		String formatISO = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";
		String format1 = "yyyy.MM.dd HH:mm:ss";
		String format2 = "yyyy-MM-dd";
		
		request.setTime(Arrays.asList((new DateTime(timeStr).toDate())));
		WebMap map = null;
		
		transformer.setTimeFormatPattern(format1);
		DateTimeFormatter formatterISO = DateTimeFormat.forPattern(formatISO);
		DateTimeFormatter formatter = DateTimeFormat.forPattern(format1);
		String timeFormatted = formatterISO.parseDateTime(timeStr).toString(formatter);
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "timeStart", timeFormatted);
			assertViewParamSet(request, "timeEnd", timeFormatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
		
		transformer.setTimeFormatPattern(format2);
		formatter = DateTimeFormat.forPattern(format2);
		timeFormatted = formatterISO.parseDateTime(timeStr).toString(formatter);
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "timeStart", timeFormatted);
			assertViewParamSet(request, "timeEnd", timeFormatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
		
	}
	
	@Test
	public void testElevationFormatting() throws Exception {
		String elevStr = "1000";
		String format1 = "%06.4f";
		String format2 = "%4.2f";
		
		request.setElevation(Arrays.asList(new Double(elevStr)));
		WebMap map = null;
		
		transformer.setElevationFormatPattern(format1);
		String elevFormatted = String.format(format1, Double.parseDouble(elevStr));
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "elevationStart", elevFormatted);
			assertViewParamSet(request, "elevationEnd", elevFormatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
		
		transformer.setElevationFormatPattern(format2);
		elevFormatted = String.format(format2, Double.parseDouble(elevStr));
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "elevationStart", elevFormatted);
			assertViewParamSet(request, "elevationEnd", elevFormatted);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	
	
	static void assertViewParamSet(GetMapRequest req, String name, String expected) {
		boolean found = false;
		boolean valueEqual = false;
		List<String> nonMatchingValues = new ArrayList<String>();
		List<Map<String, String>> vps = req.getViewParams();
		if (vps != null) {
			found = true;
			valueEqual = true;
			for (Map<String, String> m:vps) {
				if (!m.containsKey(name)) {
					found = false;
				} else if (!expected.equals(m.get(name))){
					valueEqual = false;
					nonMatchingValues.add(m.get(name));
				}
			}
		}
		if (!found) {
			fail("ViewParam '" + name + "' not set for all layers");
		} else if (!valueEqual) {
			fail("value '" + expected + "' expected for view param '" + name + "' but instead got these values for some layers: " + nonMatchingValues.toString());
		}
		
	}
	
	static void assertViewParamNotSet(GetMapRequest req, String name) {
		boolean found = false;
		List<Map<String, String>> vps = req.getViewParams();
		if (vps != null) {
			for (Map<String, String> m:vps) {
				if (m.containsKey(name)) {
					found = true;
					break;
				}
			}
		}
		if (found) {
			fail("ViewParam '" + name + "' is set at least for some layers, but should not be");
		}
		
	}
	
	static void setCustomDimensionValue(GetMapRequest request, String dimensionName, String value) {
		Map<String, String> kvp = request.getRawKvp();
		kvp.put("DIM_" + dimensionName, value);
		request.setRawKvp(kvp);
	}
	
	static void removeCustomDimension(GetMapRequest request, String dimensionName) {
		Map<String, String> kvp = request.getRawKvp();
		kvp.remove("DIM_" + dimensionName);
		request.setRawKvp(kvp);
	}

}
