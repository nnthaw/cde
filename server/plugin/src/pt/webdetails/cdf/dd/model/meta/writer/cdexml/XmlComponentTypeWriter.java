/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.webdetails.cdf.dd.model.meta.writer.cdexml;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Branch;
import org.dom4j.Element;
import pt.webdetails.cdf.dd.model.meta.ComponentType;
import pt.webdetails.cdf.dd.model.core.Attribute;
import pt.webdetails.cdf.dd.model.core.Thing;
import pt.webdetails.cdf.dd.model.meta.PropertyType;
import pt.webdetails.cdf.dd.model.meta.PropertyTypeUsage;
import pt.webdetails.cdf.dd.model.meta.Resource;
import pt.webdetails.cdf.dd.model.core.UnsupportedThingException;
import pt.webdetails.cdf.dd.model.core.writer.IThingWriteContext;
import pt.webdetails.cdf.dd.model.core.writer.IThingWriter;
import pt.webdetails.cdf.dd.model.core.writer.IThingWriterFactory;
import pt.webdetails.cdf.dd.model.core.writer.ThingWriteException;

/**
 * @author dcleao
 */
public class XmlComponentTypeWriter implements IThingWriter
{
  public void write(java.lang.Object output, IThingWriteContext context, Thing t) throws ThingWriteException
  {
    ComponentType comp = (ComponentType)t;
    Branch parent = (Branch)output; // Element or Document
    
    Element compElem = parent.addElement("DesignerComponent");

    // ----------------
    // HEADER
    Element headerElem = compElem.addElement("Header");
    headerElem.addElement("Name" ).setText(comp.getLabel());
    headerElem.addElement("IName").setText(comp.getName());
    headerElem.addElement("Description").setText(comp.getTooltip());
    headerElem.addElement("Category").setText(comp.getCategory());
    headerElem.addElement("CatDescription").setText(comp.getCategoryLabel());
    headerElem.addElement("Type").setText(comp.tryGetAttributeValue("cdePalleteType", "PalleteEntry"));
    headerElem.addElement("Version").setText(comp.getVersion());
    headerElem.addElement("Visible").setText(comp.getVisible() ? "true" : "false");

    // ----------------
    // METADATA
    Attribute cdeModelIgnoreAttr = comp.tryGetAttribute("cdeModelIgnore");
    Attribute cdeModelPrefixAttr = comp.tryGetAttribute("cdeModelPrefix");
    int ignoreCount = 0;
    if(cdeModelIgnoreAttr != null) { ignoreCount++; }
    if(cdeModelPrefixAttr != null) { ignoreCount++; }
    
    if(comp.getAttributeCount() > ignoreCount)
    {
      Element attributesElem = compElem.addElement("Metadata");
      for (Attribute attribute : comp.getAttributes())
      {
        if(!"cdeModelIgnore".equalsIgnoreCase(attribute.getName()) &&
           !"cdeModelPrefix".equalsIgnoreCase(attribute.getName()))
        {
          Element attributeElem = attributesElem.addElement("meta");
          attributeElem.setText(attribute.getValue());
          attributeElem.addAttribute("name", attribute.getName());
        }
      }
    }
    
    // ----------------
    // CONTENTS
    Element contentsElem = compElem.addElement("Contents");

    Element modelElem = contentsElem.addElement("Model");
    if(cdeModelIgnoreAttr != null) 
    {
      modelElem.addAttribute("ignore", cdeModelIgnoreAttr.getValue());
    }
    
    if(cdeModelPrefixAttr != null) 
    {
      modelElem.addAttribute("prefix", cdeModelPrefixAttr.getValue());
    }
    
    // Property Usages
    for(String defName : comp.getDefinitionNames())
    {
      Element definitionElem;
      Iterable<PropertyTypeUsage> propUsages = comp.getPropertiesByDefinition(defName);
      if(StringUtils.isEmpty(defName))
      {
        definitionElem = modelElem;
      }
      else 
      {
        definitionElem = modelElem.addElement("Definition");
        definitionElem.addAttribute("name", defName);
      }
      
      for (PropertyTypeUsage propUsage : propUsages)
      {
        String propName = propUsage.getProperty().getName();

        // TODO: exclusion of "system" properties should not be done
        // in such an explicit way.
        if(!"name".equalsIgnoreCase(propName) && !"priority".equalsIgnoreCase(propName))
        {
          Element propertyElem = definitionElem.addElement("Property");

          propertyElem.setText(propName);

          String propAlias = propUsage.getAlias();
          if(propAlias != null && !propAlias.equals(propName))
          {
            propertyElem.addAttribute("name", propAlias);
          }
        }
      }
    }
    
    // ------------------------
    // CONTENTS / IMPLEMENTATION
    Element implElem = null;

    String implPath = comp.getImplementationPath();
    if(StringUtils.isNotEmpty(implPath)) 
    {
      implElem = contentsElem.addElement("Implementation");
      
      implElem
        .addElement("Code")
        .addAttribute("src", comp.getImplementationPath());
    }
    
    if(comp.getResourceCount() > 0)
    {
      if(implElem == null) 
      { 
        implElem = contentsElem.addElement("Implementation"); 
      }
      
      Element depsElem   = implElem.addElement("Dependencies");
      Element stylesElem = implElem.addElement("Styles");
      Element rawElem    = implElem.addElement("Raw");

      for (Resource res : comp.getResources())
      {
        Element resElem;
        if(res.getType() == Resource.Type.SCRIPT)
        {
          // Script
          resElem = depsElem.addElement("Dependency");

          resElem.addAttribute("src", res.getSource());
          resElem.setText(res.getName());
        }
        else if(res.getType() == Resource.Type.STYLE)
        {
          resElem = stylesElem.addElement("Style");

          resElem.addAttribute("src", res.getSource());
          resElem.setText(res.getName());
        }
        else if(res.getType() == Resource.Type.RAW)
        {
          resElem = rawElem.addElement("Code"); // TODO: couldn't find any example using this so I made up the item tag name "Code"...

          resElem.addAttribute("name", res.getName());
          resElem.setText(res.getSource());
        }
        else
        {
          continue;
        }

        resElem
           .addAttribute("version", res.getVersion())
           .addAttribute("app", res.getApp());
      }
      
      if(!depsElem.hasContent()  ) { implElem.remove(depsElem);   }
      if(!stylesElem.hasContent()) { implElem.remove(stylesElem); }
      if(!rawElem.hasContent()   ) { implElem.remove(rawElem);    }
    }
      
    // OwnProperties
    if(comp.getPropertyUsageCount() > 0)
    {
      Element custPropsElem = null;

      IThingWriterFactory factory = context.getFactory();
      for(PropertyTypeUsage propUsage : comp.getPropertyUsages())
      {
        if(propUsage.isOwned())
        {
          if(custPropsElem == null) 
          {
            if(implElem == null)
            {
              implElem = contentsElem.addElement("Implementation");
            }
            
            custPropsElem = implElem.addElement("CustomProperties");
          }

          PropertyType prop = propUsage.getProperty();
          IThingWriter writer;
          try
          {
            writer = factory.getWriter(prop);
          }
          catch (UnsupportedThingException ex)
          {
            throw new ThingWriteException(ex);
          }

          writer.write(custPropsElem, context, prop);
        }
      }
    }
  }
}
