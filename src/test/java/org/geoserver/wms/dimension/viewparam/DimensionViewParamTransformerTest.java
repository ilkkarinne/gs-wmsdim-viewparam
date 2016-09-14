package org.geoserver.wms.dimension.viewparam;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.geoserver.wms.GetMap;
import org.geoserver.wms.GetMapOutputFormat;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.WMS;
import org.geoserver.wms.WMSMockData;
import org.geoserver.wms.WMSTestSupport;
import org.geoserver.wms.WebMap;
import org.geotools.util.DateRange;
import org.joda.time.DateTime;
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
        final DummyRasterMapProducer producer = new DummyRasterMapProducer();
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
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "timeStart", timeStr);
			assertViewParamSet(request, "timeEnd", timeStr);
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
				)
		);
		WebMap map = null;
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "timeStart", timeBeginStr);
			assertViewParamSet(request, "timeEnd", timeEndStr);
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
		try {
			map = getMapOp.run(request);
			assertViewParamSet(request, "elevationStart", elevStr);
		} finally {
			if (map != null) {
				map.dispose();
			}
		}
	}
	
	static void assertViewParamSet(GetMapRequest req, String name, String expected) {
		boolean found = false;
		boolean valueEqual = false;
		List<Map<String, String>> vps = req.getViewParams();
		if (vps != null) {
			found = true;
			valueEqual = true;
			for (Map<String, String> m:vps) {
				if (!m.containsKey(name)) {
					found = false;
					break;
				} else if (!expected.equals(m.get(name))){
					valueEqual = false;
					break;
				}
			}
		}
		if (!found) {
			fail("ViewParam '" + name + "' not set");
		} else if (!valueEqual) {
			fail("ViewParam '" + name + "' has inconsistent or unexpected values");
		}
		
	}

}
