package org.apache.maven.plugin.surefire;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.surefire.booter.Classpath;
import org.apache.maven.surefire.booter.KeyValueSource;
import org.apache.maven.surefire.util.internal.StringUtils;

import static java.util.Arrays.asList;

/**
 * A properties implementation that preserves insertion order.
 */
public class SurefireProperties
    extends Properties
    implements KeyValueSource
{
    private static final Collection<String> KEYS_THAT_CANNOT_BE_USED_AS_SYSTEM_PROPERTIES =
            asList( "java.library.path", "file.encoding", "jdk.map.althashing.threshold", "line.separator" );

    private final LinkedHashSet<Object> items = new LinkedHashSet<Object>();

    public SurefireProperties()
    {
    }

    public SurefireProperties( Properties source )
    {
        if ( source != null )
        {
            this.putAll( source );
        }
    }

    public SurefireProperties( KeyValueSource source )
    {
        if ( source != null )
        {
            source.copyTo( this );
        }
    }

    @Override
    public synchronized Object put( Object key, Object value )
    {
        items.add( key );
        return super.put( key, value );
    }

    @Override
    public synchronized Object remove( Object key )
    {
        items.remove( key );
        return super.remove( key );
    }

    @Override
    public synchronized void clear()
    {
        items.clear();
        super.clear();
    }

    @Override
    public synchronized Enumeration<Object> keys()
    {
        return Collections.enumeration( items );
    }

    public void copyPropertiesFrom( Properties source )
    {
        if ( source != null )
        {
            putAll( source );
        }
    }

    public Iterable<Object> getStringKeySet()
    {
        //noinspection unchecked
        return keySet();
    }

    public Set<Object> propertiesThatCannotBeSetASystemProperties()
    {
        Set<Object> result = new HashSet<Object>();
        for ( Object key : getStringKeySet() )
        {
            if ( KEYS_THAT_CANNOT_BE_USED_AS_SYSTEM_PROPERTIES.contains( key ) )
            {
                result.add( key );
            }
        }
        return result;
    }

    public void copyToSystemProperties()
    {

        //noinspection unchecked
        for ( Object o : items )
        {
            String key = (String) o;
            String value = getProperty( key );

            System.setProperty( key, value );
        }
    }

    static SurefireProperties calculateEffectiveProperties( Properties systemProperties,
                                                            Map<String, String> systemPropertyVariables,
                                                            Properties userProperties, SurefireProperties props )
    {
        SurefireProperties result = new SurefireProperties();
        result.copyPropertiesFrom( systemProperties );

        result.copyPropertiesFrom( props );

        copyProperties( result, systemPropertyVariables );

        // We used to take all of our system properties and dump them in with the
        // user specified properties for SUREFIRE-121, causing SUREFIRE-491.
        // Not gonna do THAT any more... instead, we only propagate those system properties
        // that have been explicitly specified by the user via -Dkey=value on the CLI

        result.copyPropertiesFrom( userProperties );
        return result;
    }

    public static void copyProperties( Properties target, Map<String, String> source )
    {
        if ( source != null )
        {
            for ( String key : source.keySet() )
            {
                String value = source.get( key );
                target.setProperty( key, value == null ? "" : value );
            }
        }
    }

    @Override
    public void copyTo( Map<Object, Object> target )
    {
        target.putAll( this );
    }

    public void setProperty( String key, File file )
    {
        if ( file != null )
        {
            setProperty( key, file.toString() );
        }
    }

    public void setProperty( String key, Boolean aBoolean )
    {
        if ( aBoolean != null )
        {
            setProperty( key, aBoolean.toString() );
        }
    }

    public void addList( List<?> items, String propertyPrefix )
    {
        if ( items != null && !items.isEmpty() )
        {
            int i = 0;
            for ( Object item : items )
            {
                if ( item == null )
                {
                    throw new NullPointerException( propertyPrefix + i + " has null value" );
                }

                String[] stringArray = StringUtils.split( item.toString(), "," );

                for ( String aStringArray : stringArray )
                {
                    setProperty( propertyPrefix + i, aStringArray );
                    i++;
                }
            }
        }
    }

    public void setClasspath( String prefix, Classpath classpath )
    {
        List<String> classpathElements = classpath.getClassPath();
        for ( int i = 0; i < classpathElements.size(); ++i )
        {
            String element = classpathElements.get( i );
            setProperty( prefix + i, element );
        }
    }

    private static SurefireProperties loadProperties( InputStream inStream )
        throws IOException
    {
        try
        {
            Properties p = new Properties();
            p.load( inStream );
            return new SurefireProperties( p );
        }
        finally
        {
            close( inStream );
        }
    }

    public static SurefireProperties loadProperties( File file )
        throws IOException
    {
        return file == null ? new SurefireProperties() : loadProperties( new FileInputStream( file ) );
    }

    private static void close( InputStream inputStream )
    {
        try
        {
            inputStream.close();
        }
        catch ( IOException ex )
        {
            // ignore
        }
    }

    public void setNullableProperty( String key, String value )
    {
        if ( value != null )
        {
            super.setProperty( key, value );
        }
    }
}
