/*

   Licensed Materials - Property of IBM
   Cloudscape - Package org.apache.derby.iapi.sql.dictionary
   (C) Copyright IBM Corp. 1997, 2004. All Rights Reserved.
   US Government Users Restricted Rights - Use, duplication or
   disclosure restricted by GSA ADP Schedule Contract with IBM Corp.

 */

package org.apache.derby.iapi.sql.dictionary;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.types.DataValueDescriptor;
import org.apache.derby.iapi.types.DataValueFactory;

/**
 * Implements the description of a column in a system table.
 *
 *
 * @version 0.1
 * @author Rick Hillegas
 */

public interface SystemColumn
{
	/**
	 * Gets the name of this column.
	 *
	 * @return	The column name.
	 */
	public String	getName();

	/**
	 * Gets the id of this column.
	 *
	 * @return	The column id.
	 */
	public int	getID();

	/**
	 * Gets the precision of this column.
	 *
	 * @return	The precision of data stored in this column.
	 */
	public int	getPrecision();

	/**
	 * Gets the scale of this column.
	 *
	 * @return	The scale of data stored in this column.
	 */
	public int	getScale();

	/**
	 * Gets the nullability of this column.
	 *
	 * @return	True if this column is nullable. False otherwise.
	 */
	public boolean	getNullability();

	/**
	 * Gets the datatype of this column.
	 *
	 * @return	The datatype of this column.
	 */
	public String	getDataType();

	/**
	 * Is it a built-in type?
	 *
	 * @return	True if it's a built-in type.
	 */
	public boolean	builtInType();

	/**
	 * Gets the maximum length of this column.
	 *
	 * @return	The maximum length of data stored in this column.
	 */
	public int	getMaxLength();
}

