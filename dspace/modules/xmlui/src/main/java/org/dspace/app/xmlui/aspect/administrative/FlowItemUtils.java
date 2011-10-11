/*
 * FlowItemUtils.java
 *
 * Version: $Revision: 4500 $
 *
 * Date: $Date: 2009-11-03 03:15:38 +0100 (di, 03 nov 2009) $
 *
 * Copyright (c) 2002-2009, The DSpace Foundation.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Neither the name of the DSpace Foundation nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 */
package org.dspace.app.xmlui.aspect.administrative;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;

import org.apache.cocoon.environment.Request;
import org.apache.cocoon.servlet.multipart.Part;
import org.apache.log4j.Logger;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.*;
import org.dspace.content.authority.Choices;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.handle.HandleManager;
import org.dspace.embargo.EmbargoManager;

/**
 * Utility methods to processes actions on Groups. These methods are used
 * exclusivly from the administrative flow scripts.
 *
 * @author Jay Paz
 * @author Scott Phillips
 *
 * This class has been altered to support the embargo functionallity
 */
public class FlowItemUtils
{
    private static final Logger log = Logger.getLogger(FlowItemUtils.class);

	/** Language Strings */
	private static final Message T_metadata_updated = new Message("default","The Item's metadata was successfully updated.");
	private static final Message T_metadata_added = new Message("default","New metadata was added.");
	private static final Message T_embargo_set = new Message("default","New embargo was set.");
	private static final Message T_embargo_removed = new Message("default","Embargo was disabled.");
	private static final Message T_embargo_not_set = new Message("default","The embargo could not be configured. Please make sure you have a valid future date.");
	private static final Message T_item_withdrawn = new Message("default","The item has been withdrawn.");
	private static final Message T_item_reinstated = new Message("default","The item has been reinstated.");
    private static final Message T_item_moved = new Message("default","The item has been moved.");
    private static final Message T_item_move_destination_not_found = new Message("default","The selected destination collection could not be found.");
	private static final Message T_bitstream_added = new Message("default","The new bitstream was successfully uploaded.");
	private static final Message T_bitstream_failed = new Message("default","Error while uploading file.");
	private static final Message T_bitstream_updated = new Message("default","The bitstream has been updated.");
	private static final Message T_bitstream_delete = new Message("default","The selected bitstreams have been deleted.");


	/**
	 * Resolve the given identifier to an item. The identifier may be either an
	 * internal ID or a handle. If an item is found then the result the internal
	 * ID of the item will be placed in the result "itemID" parameter.
	 *
	 * If the identifier was unable to be resolved to an item then the "identifier"
	 * field is placed in error.
	 *
	 * @param context The current DSpace context.
	 * @param identifier An Internal ID or a handle
	 * @return A flow result
	 */
	public static FlowResult resolveItemIdentifier(Context context, String identifier) throws SQLException
	{
		FlowResult result = new FlowResult();
		result.setContinue(false);

		//		Check whether it's a handle or internal id (by check ing if it has a slash inthe string)
		if (identifier.contains("/"))
		{
			DSpaceObject dso = HandleManager.resolveToObject(context, identifier);

			if (dso != null && dso.getType() == Constants.ITEM)
			{
				result.setParameter("itemID", dso.getID());
				result.setParameter("type", Constants.ITEM);
				result.setContinue(true);
				return result;
			}
		}
		else
		{

			Item item = null;
			try {
				item = Item.find(context, Integer.valueOf(identifier));
			} catch (NumberFormatException e) {
                log.error(LogManager.getHeader(context, "Error while resolving item identifier", "identifier: " + identifier), e);
			}

			if (item != null)
			{
				result.setParameter("itemID", item.getID());
				result.setParameter("type", Constants.ITEM);
				result.setContinue(true);
				return result;
			}
		}

		result.addError("identifier");
		return result;
	}

	/**
	 * Process the request parameters to update the item's metadata and remove any selected bitstreams.
	 *
	 * Each metadata entry will have three fields "name_X", "value_X", and "language_X" where X is an
	 * integer that relates all three of the fields together. The name parameter stores the metadata name
	 * that is used by the entry (i.e schema_element_qualifier). The value and language paramaters are user
	 * inputed fields. If the optional parameter "remove_X" is given then the metadata value is removed.
	 *
	 * To support AJAX operations on this page an aditional parameter is considered, the "scope". The scope
	 * is the set of metadata entries that are being updated during this request. It the metadata name,
	 * schema_element_qualifier, only fields that have this name are considered! If all fields are to be
	 * considered then scope should be set to "*".
	 *
	 * When creating an AJAX query include all the name_X, value_X, language_X, and remove_X for the fields
	 * in the set, and then set the scope parameter to be the metadata field.
	 *
	 * @param context The current DSpace context
	 * @param itemID  internal item id
	 * @param request the Cocoon request
	 * @return A flow result
	 */
	public static FlowResult processEditItem(Context context, int itemID, Request request) throws SQLException, AuthorizeException, UIException, IOException
	{
		FlowResult result = new FlowResult();
		result.setContinue(false);

		Item item = Item.find(context, itemID);


		// STEP 1:
		// Clear all metadata within the scope
		// Only metadata values within this scope will be considered. This
		// is so ajax request can operate on only a subset of the values.
		String scope = request.getParameter("scope");
		if ("*".equals(scope))
		{
			item.clearMetadata(Item.ANY, Item.ANY, Item.ANY, Item.ANY);
		}
		else
		{
			String[] parts = parseName(scope);
			item.clearMetadata(parts[0],parts[1],parts[2],Item.ANY);
		}

		// STEP 2:
		// First determine all the metadata fields that are within
		// the scope parameter
		ArrayList<Integer> indexes = new ArrayList<Integer>();
		Enumeration parameters = request.getParameterNames();
		while(parameters.hasMoreElements())
		{

			// Only consider the name_ fields
			String parameterName = (String) parameters.nextElement();
			if (parameterName.startsWith("name_"))
			{
				// Check if the name is within the scope
				String parameterValue = request.getParameter(parameterName);
				if ("*".equals(scope) || scope.equals(parameterValue))
				{
					// Extract the index from the name.
					String indexString = parameterName.substring("name_".length());
					Integer index = Integer.valueOf(indexString);
					indexes.add(index);
				}
			}
		}

		// STEP 3:
		// Iterate over all the indexes within the scope and add them back in.
		for (Integer index=1; index <= indexes.size(); ++index)
		{
			String name = request.getParameter("name_"+index);
			String value = request.getParameter("value_"+index);
                        String authority = request.getParameter("value_"+index+"_authority");
                        String confidence = request.getParameter("value_"+index+"_confidence");
			String lang = request.getParameter("language_"+index);
			String remove = request.getParameter("remove_"+index);

			// the user selected the remove checkbox.
			if (remove != null)
				continue;

			// get the field's name broken up
			String[] parts = parseName(name);

                        // probe for a confidence value
                        int iconf = Choices.CF_UNSET;
                        if (confidence != null && confidence.length() > 0)
                            iconf = Choices.getConfidenceValue(confidence);
                        // upgrade to a minimum of NOVALUE if there IS an authority key
                        if (authority != null && authority.length() > 0 && iconf == Choices.CF_UNSET)
                            iconf = Choices.CF_NOVALUE;
                        item.addMetadata(parts[0], parts[1], parts[2], lang,
                                             value, authority, iconf);
		}

		item.update();
		context.commit();

		result.setContinue(true);

		result.setOutcome(true);
		result.setMessage(T_metadata_updated);

		return result;
	}

	/**
	 * Process the request paramaters to add a new metadata entry for the item.
	 *
	 * @param context The current DSpace context
	 * @param itemID  internal item id
	 * @param request the Cocoon request
	 * @return A flow result
	 */
	public static FlowResult processAddMetadata(Context context, int itemID, Request request) throws SQLException, AuthorizeException, UIException, IOException
	{
		FlowResult result = new FlowResult();
		result.setContinue(false);

		Item item = Item.find(context, itemID);


		String fieldID = request.getParameter("field");
		String value = request.getParameter("value");
		String language = request.getParameter("language");

		MetadataField field = MetadataField.find(context,Integer.valueOf(fieldID));
		MetadataSchema schema = MetadataSchema.find(context,field.getSchemaID());

		item.addMetadata(schema.getName(), field.getElement(), field.getQualifier(), language, value);

		item.update();
		context.commit();

		result.setContinue(true);

		result.setOutcome(true);
		result.setMessage(T_metadata_added);

		return result;
	}

    /**
     * Process the request parameters for the embargo of the item
     * @param context the current dspace context
     * @param itemID internal item id
     * @param request the Cocoon request
     * @return A flow result
     */
    public static FlowResult processEditEmbargo(Context context, int itemID, Request request) throws SQLException, AuthorizeException, IOException {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        Item item = Item.find(context, itemID);

        //Clear it anyways if we got a new it will be set later on
        if("enabled".equals(request.getParameter("embargo"))){
            try{
//                int day = Integer.parseInt(request.getParameter("date_day"));
//                int month = Integer.parseInt(request.getParameter("date_month"));
//                int year = Integer.parseInt(request.getParameter("date_year"));

                //Check if we have a valid date
//                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
//                dateFormat.setLenient(false);
//                String dateStr = year + "-" + fleshOut(month) + "-" + fleshOut(day);
//                Date embargoDateDate = dateFormat.parse(dateStr);

                //We don't want a day before today
//                Calendar today = Calendar.getInstance();
//                if(embargoDateDate.before(today.getTime()))
//                    throw new IllegalArgumentException();

                //First check if we already have an embargo
                DCDate currentEmbargoDate = EmbargoManager.getEmbargoDate(context, item);
                if(currentEmbargoDate == null){
                    //We don't have an embargo at the moment so create a new one, a year in the future
                    Calendar embargoDate = Calendar.getInstance();
                    //Add one year
                    embargoDate.add(Calendar.YEAR, 1);
                    //Store it
                    //Create and store our embargodate
                    DCDate embargoDcDate = new DCDate();
                    embargoDcDate.setDateLocal(embargoDate.get(Calendar.YEAR), embargoDate.get(Calendar.MONTH) + 1, embargoDate.get(Calendar.DATE), -1, -1, -1);
                    EmbargoManager.setEmbargo(context, item, embargoDcDate);
                }


            }catch(Exception e){
                result.setContinue(true);
                result.setOutcome(false);
                result.setMessage(T_embargo_not_set);
                return result;
            }
        }else
            EmbargoManager.liftEmbargo(context, item);

//        BitstreamUtil.setBitstreamEmbargos(context, item, true);

        item.update();
        context.commit();

        result.setContinue(true);
        result.setOutcome(true);
        if("enabled".equals(request.getParameter("embargo"))){
            result.setMessage(T_embargo_set);
        } else {
            result.setMessage(T_embargo_removed);
        }

        return result;

    }

    /**
     * Flesh out a number to two digits
     *
     * @param n
     *            the number
     * @return the number as a two-digit string
     */
    private static String fleshOut(int n)
    {
        if (n < 10)
        {
            return "0" + n;
        }
        else
        {
            return String.valueOf(n);
        }
    }

	/**
	 * Withdraw the specified item, this method assumes that the action has been confirmed.
	 *
	 * @param context The DSpace context
	 * @param itemID The id of the to-be-withdrawn item.
	 * @return A result object
	 */
	public static FlowResult processWithdrawItem(Context context, int itemID) throws SQLException, AuthorizeException, IOException
	{
		FlowResult result = new FlowResult();
		result.setContinue(false);

		Item item = Item.find(context, itemID);
		item.withdraw();
		context.commit();

		result.setContinue(true);
        result.setOutcome(true);
        result.setMessage(T_item_withdrawn);

		return result;
	}


	/**
	 * Reinstate the specified item, this method assumes that the action has been confirmed.
	 *
	 * @param context The DSpace context
	 * @param itemID The id of the to-be-reinstated item.
	 * @return A result object
	 */
	public static FlowResult processReinstateItem(Context context, int itemID) throws SQLException, AuthorizeException, IOException
	{
		FlowResult result = new FlowResult();
		result.setContinue(false);

		Item item = Item.find(context, itemID);
		item.reinstate();
		context.commit();

		result.setContinue(true);
        result.setOutcome(true);
        result.setMessage(T_item_reinstated);

		return result;
	}


    /**
     * Move the specified item to another collection.
     *
     * @param context The DSpace context
     * @param itemID The id of the to-be-moved item.
     * @param collectionID The id of the destination collection.
     * @return A result object
     */
    public static FlowResult processMoveItem(Context context, int itemID, int collectionID) throws SQLException, AuthorizeException, IOException
    {
        FlowResult result = new FlowResult();
        result.setContinue(false);

        Item item = Item.find(context, itemID);

        if(AuthorizeManager.isAdmin(context, item))
        {
          //Add a policy giving this user *explicit* admin permissions on the item itself.
          //This ensures that the user will be able to call item.update() even if he/she
          // moves it to a Collection that he/she doesn't administer.
          AuthorizeManager.addPolicy(context, item, Constants.ADMIN, context.getCurrentUser());

          Collection destination = Collection.find(context, collectionID);
          if (destination == null)
          {
              result.setOutcome(false);
              result.setContinue(false);
              result.setMessage(T_item_move_destination_not_found);
              return result;
          }

          Collection owningCollection = item.getOwningCollection();
          if (destination.equals(owningCollection))
          {
              // nothing to do
              result.setOutcome(false);
              result.setContinue(false);
              return result;
          }

          // note: an item.move() method exists, but does not handle several cases:
          // - no preexisting owning collection (first arg is null)
          // - item already in collection, but not an owning collection
          //   (works, but puts item in collection twice)

          // Don't re-add the item to a collection it's already in.
          boolean alreadyInCollection = false;
          for (Collection collection : item.getCollections())
          {
              if (collection.equals(destination))
              {
                  alreadyInCollection = true;
                  break;
              }
          }

          // Remove item from its owning collection and add to the destination
          if (!alreadyInCollection)
              destination.addItem(item);

          if (owningCollection != null)
              owningCollection.removeItem(item);

          item.setOwningCollection(destination);
          item.update();
          context.commit();

          result.setOutcome(true);
          result.setContinue(true);
          result.setMessage(T_item_moved);
        }

        return result;
    }


	/**
	 * Permanently delete the specified item, this method assumes that
	 * the action has been confirmed.
	 *
	 * @param context The DSpace context
	 * @param itemID The id of the to-be-deleted item.
	 * @return A result object
	 */
	public static FlowResult processDeleteItem(Context context, int itemID) throws SQLException, AuthorizeException, IOException
	{
		FlowResult result = new FlowResult();
		result.setContinue(false);

		Item item = Item.find(context, itemID);

        Collection[] collections = item.getCollections();

        // Remove item from all the collections it's in
        for (Collection collection : collections)
        {
            collection.removeItem(item);
        }

        // Note: when removing an item from the last collection it will
        // be removed from the system. So there is no need to also call
        // an item.delete() method.

        context.commit();

        result.setContinue(true);

		return result;
	}


	/**
	 * Add a new bitstream to the item. The bundle, bitstream (aka file), and description
	 * will be used to create a new bitstream. If the format needs to be adjusted then they
	 * will need to access the edit bitstream form after it has been uploaded.
	 *
	 * @param context The DSpace content
	 * @param itemID The item to add a new bitstream too
	 * @param request The request.
	 * @return A flow result
	 */
	public static FlowResult processAddBitstream(Context context, int itemID, Request request) throws SQLException, AuthorizeException, IOException
	{
		FlowResult result = new FlowResult();
		result.setContinue(false);

		// Upload a new file
		Item item = Item.find(context, itemID);


		Object object = request.get("file");
		Part filePart = null;
		if (object instanceof Part)
			filePart = (Part) object;

		if (filePart != null && filePart.getSize() > 0)
		{
			InputStream is = filePart.getInputStream();

			String bundleName = request.getParameter("bundle");

			Bitstream bitstream;
			Bundle[] bundles = item.getBundles(bundleName);
			if (bundles.length < 1)
			{
				// set bundle's name to ORIGINAL
				bitstream = item.createSingleBitstream(is, bundleName);

				// set the permission as defined in the owning collection
				Collection owningCollection = item.getOwningCollection();
				if (owningCollection != null)
				{
				    Bundle bnd = bitstream.getBundles()[0];
				    bnd.inheritCollectionDefaultPolicies(owningCollection);
				}
			}
			else
			{
				// we have a bundle already, just add bitstream
				bitstream = bundles[0].createBitstream(is);
			}

			// Strip all but the last filename. It would be nice
			// to know which OS the file came from.
			String name = filePart.getUploadName();

			while (name.indexOf('/') > -1)
			{
				name = name.substring(name.indexOf('/') + 1);
			}

			while (name.indexOf('\\') > -1)
			{
				name = name.substring(name.indexOf('\\') + 1);
			}

			bitstream.setName(name);
			bitstream.setSource(filePart.getUploadName());
			bitstream.setDescription(request.getParameter("description"));

			// Identify the format
			BitstreamFormat format = FormatIdentifier.guessFormat(context, bitstream);
			bitstream.setFormat(format);

//            BitstreamUtil.setBitstreamEmbargos(context, item, true);

			// Update to DB
			bitstream.update();
			item.update();

			context.commit();

			result.setContinue(true);
	        result.setOutcome(true);
	        result.setMessage(T_bitstream_added);
		}
		else
		{
			result.setContinue(false);
	        result.setOutcome(false);
	        result.setMessage(T_bitstream_failed);
		}
		return result;
	}


	/**
	 * Update a bitstream's metadata.
	 *
	 * @param context The DSpace content
	 * @param itemID The item to which the bitstream belongs
	 * @param bitstreamID The bitstream being updated.
	 * @param description The new description of the bitstream
	 * @param formatID The new format ID of the bitstream
	 * @param userFormat Any user supplied formats.
	 * @return A flow result object.
	 */
	public static FlowResult processEditBitstream(Context context, int itemID, int bitstreamID, String primary, String description, int formatID, String userFormat) throws SQLException, AuthorizeException
	{
		FlowResult result = new FlowResult();
		result.setContinue(false);

		Bitstream bitstream = Bitstream.find(context, bitstreamID);
		BitstreamFormat currentFormat = bitstream.getFormat();

		//Step 1:
		// Update the bitstream's description
		if (description != null)
		{
			bitstream.setDescription(description);
		}

		//Step 2:
		// Check if the primary bitstream status has changed
		Bundle[] bundles = bitstream.getBundles();
		if (bundles != null && bundles.length > 0)
		{
			if (bitstreamID == bundles[0].getPrimaryBitstreamID())
			{
				// currently the bitstream is primary
				if ("no".equals(primary))
				{
					// However the user has removed this bitstream as a primary bitstream.
					bundles[0].unsetPrimaryBitstreamID();
					bundles[0].update();
				}
			}
			else
			{
				// currently the bitstream is non-primary
				if ("yes".equals(primary))
				{
					// However the user has set this bitstream as primary.
					bundles[0].setPrimaryBitstreamID(bitstreamID);
					bundles[0].update();
				}
			}
		}


		//Step 2:
		// Update the bitstream's format
		if (formatID > 0)
		{
			if (currentFormat == null || currentFormat.getID() != formatID)
			{
				BitstreamFormat newFormat = BitstreamFormat.find(context, formatID);
				if (newFormat != null)
				{
					bitstream.setFormat(newFormat);
				}
			}
		}
		else
		{
			if (userFormat != null && userFormat.length() > 0)
			{
				bitstream.setUserFormatDescription(userFormat);
			}
		}

		//Step 3:
		// Save our changes
		bitstream.update();
		context.commit();

		 result.setContinue(true);
	     result.setOutcome(true);
	     result.setMessage(T_bitstream_updated);


		return result;
	}

	/**
	 * Delete the given bitstreams from the bundle and item. If there are no more bitstreams
	 * left in a bundle then also remove it.
	 *
	 * @param context Current dspace content
	 * @param itemID The item id from which to remove bitstreams
	 * @param bitstreamIDs A bundle slash bitstream id pair of bitstreams to be removed.
	 * @return A flow result
	 */
	public static FlowResult processDeleteBitstreams(Context context, int itemID, String[] bitstreamIDs) throws SQLException, AuthorizeException, IOException, UIException
	{
		FlowResult result = new FlowResult();
		result.setContinue(false);

		Item item = Item.find(context, itemID);

		for (String id : bitstreamIDs)
		{
			String[] parts = id.split("/");

			if (parts.length != 2)
				throw new UIException("Unable to parse id into bundle and bitstream id: "+id);

			int bundleID = Integer.valueOf(parts[0]);
			int bitstreamID = Integer.valueOf(parts[1]);

			Bundle bundle = Bundle.find(context, bundleID);
			Bitstream bitstream = Bitstream.find(context,bitstreamID);

			bundle.removeBitstream(bitstream);

			if (bundle.getBitstreams().length == 0)
			{
				item.removeBundle(bundle);
			}
		}

		item.update();

		context.commit();

		result.setContinue(true);
		result.setOutcome(true);
		result.setMessage(T_bitstream_delete);

		return result;
	}


	/**
	 * Parse the given name into three parts, divided by an _. Each part should represent the
	 * schema, element, and qualifier. You are guaranteed that if no qualifier was supplied the
	 * third entry is null.
	 *
	 * @param name The name to be parsed.
	 * @return An array of name parts.
	 */
	private static String[] parseName(String name) throws UIException
	{
		String[] parts = new String[3];

		String[] split = name.split("_");
		if (split.length == 2) {
			parts[0] = split[0];
			parts[1] = split[1];
			parts[2] = null;
		} else if (split.length == 3) {
			parts[0] = split[0];
			parts[1] = split[1];
			parts[2] = split[2];
		} else {
			throw new UIException("Unable to parse metedata field name: "+name);
		}
		return parts;
	}
}