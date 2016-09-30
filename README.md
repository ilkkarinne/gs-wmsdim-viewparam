# Geoserver WMS Dimension Parameter to View Parameter Module 
Geoserver module for copying the dimension (TIME, ELEVATION or DIM_*) request parameters of WMS GetMap requests into SQL view parameters for to be used in SQL views or by other data sources.

This module is used to inject dimension request parameter values into the WMS GetMap request as [SQL view parameters](http://docs.geoserver.org/latest/en/user/data/database/sqlview.html). This is useful for data sources which need to be passed the dimension request parameters to do remote result filtering, request tracking, or other functionality, and cannot make use of the feature filter objects created by the Geoserver for this for some reason.

Author: Ilkka Rinne / Spatineo 2016 for the Finnish Meteorological Institute

## Parameter mapping

### TIME Dimension

The following SQL view parameters are injected for every request layer by the plugin by default for GetMap request containing a TIME request parameter:

* **timeStart**: the beginning the given time period, or a comma separated list of begin times. For time instances, the requested instance(s) of time as comma separated list.
* **timeEnd**: the end of the given time period (inclusive), or a comma separated list of end times. For time instances, the requested instance(s) of time (same as timeStart).

The time values are encoded using ISO dateTime format (yyyy-MM-dd'T'HH:mm:ss.SSSZZ) by default. The time zone used for the encoded time values is UTC (+00:00) by default.

The view parameter names, white-listing of the layer names triggering the parameter injection, and time formatting can be modified as needed (see Changing the defaults).

### Elevation Dimension

The following SQL view parameters are injected for every request layer by the plugin by default for GetMap request containing a ELEVATION request parameter:

* **elevationStart**: the lowest value of the given elevation range, or a comma separated list of the lowest range values. For single elevations, the requested elevation(s) as comma separated list.
* **elevationEnd**: the highest value of the given elevation range (inclusive), or a comma separated list of highest range values. For single elevations, the list of the requested elevations(s) (same as elevationStart).

The numerical elevation values are encoded as floating point numbers in decimal notation with three decimals (%.3f) by default.

The view parameter names, white-listing of the layer names triggering the parameter injection, and number formatting can be modified as needed (see Changing the defaults).

### Custom dimensions

All GetMap custom dimension request parameters (names starting with "DIM_") are injected as view parameters with the same name and textual value(s) as comma separated list by default.

The mapping between the custom dimension and view parameter parameter names can be modified as needed (see Changing the defaults).

## Changing the defaults

At the moment, there is no configuration settings in the Geoserver Web administration interface for this module. The idea is to add these setting under the [WMS settings page](docs.geoserver.org/latest/en/services/wms/webadmin.html).

If you need to change the defaults, you can do so be configuring the transformer bean's Spring application context in `src/main/resources/applicationContext.xml`:

    <?xml version="1.0" encoding="UTF-8"?>
    <!-- Copyright (c) 2013 OpenPlans - www.openplans.org. All rights reserved. 
    This code is licensed under the GPL 2.0 license, available at the root application 
    directory. -->
    <!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN//EN" "http://www.springframework.org/dtd/spring-beans.dtd">
    <beans>
      <!-- GetMap callback -->
      <bean id="getMapCallback"
        class="org.geoserver.wms.dimension.viewparam.DimensionSQLViewParamRequestTransformer">
        <property name="transformTimeEnabled" value="true" />
        <property name="transformElevationEnabled" value="false" />
        <property name="overrideExistingViewParams" value="true" />
        <property name="timeZoneById" value="EEST" />
        <property name="timeFormatPattern" value="yyyy-MM-dd" />
        <property name="elevationFormatPattern" value="%.5f" />
    
        <property name="resourceNamesToMatch">
          <list>
            <bean class="org.geotools.feature.NameImpl">
              <constructor-arg value="http://add.full.layer.namespace.here" />
              <constructor-arg value="layerLocalName1" />
            </bean>
            <bean class="org.geotools.feature.NameImpl">
              <constructor-arg value="http://add.full.layer.namespace.here" />
              <constructor-arg value="layerLocalName2" />
            </bean>
          </list>
        </property>
    
        <property name="customDimensionsToTransform">
          <list>
            <value>testdim</value>
          </list>
        </property>
      </bean>
    </beans>


## Building and installation

This module depends on [Joda-Time](http://www.joda.org/joda-time/) for time parsing and formatting. Run maven `package` command to create a zip with both the plugin jar and the joda-time jar:

    mvn clean package
    
After the build is finished the assembly zip file is available at `target/release/geoserver-<version>-gs-vmsdim-viewparam-module.zip`

Extract the zip at the Geoserver installation main directory (containing the webapps directory), and the two jars will be extracted to `webapps/geoserver/WEB-INF/lib` subdirectory.

After restarting Geoserver, the module will be installed and enabled.

