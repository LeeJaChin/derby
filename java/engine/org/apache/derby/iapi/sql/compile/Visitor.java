/*

   Licensed Materials - Property of IBM
   Cloudscape - Package org.apache.derby.iapi.sql.compile
   (C) Copyright IBM Corp. 1998, 2004. All Rights Reserved.
   US Government Users Restricted Rights - Use, duplication or
   disclosure restricted by GSA ADP Schedule Contract with IBM Corp.

 */

package	org.apache.derby.iapi.sql.compile;

import org.apache.derby.iapi.error.StandardException;

/**
 * A visitor is an object that traverses the querytree
 * and performs some action. 
 *
 * @author jamie
 */
public interface Visitor
{
	/**
	 * This is the default visit operation on a 
	 * QueryTreeNode.  It just returns the node.  This
	 * will typically suffice as the default visit 
	 * operation for most visitors unless the visitor 
	 * needs to count the number of nodes visited or 
	 * something like that.
	 * <p>
	 * Visitors will overload this method by implementing
	 * a version with a signature that matches a specific
	 * type of node.  For example, if I want to do
	 * something special with aggregate nodes, then
	 * that Visitor will implement a 
	 * 		<I> visit(AggregateNode node)</I>
	 * method which does the aggregate specific processing.
	 *
	 * @param node 	the node to process
	 *
	 * @return a query tree node.  Often times this is
	 * the same node that was passed in, but Visitors that
	 * replace nodes with other nodes will use this to
	 * return the new replacement node.
	 *
	 * @exception StandardException may be throw an error
	 *	as needed by the visitor (i.e. may be a normal error
	 *	if a particular node is found, e.g. if checking 
	 *	a group by, we don't expect to find any ColumnReferences
	 *	that aren't under an AggregateNode -- the easiest
	 *	thing to do is just throw an error when we find the
	 *	questionable node).
	 */
	Visitable visit(Visitable node)
		throws StandardException;

	/**
	 * Method that is called to see
	 * if query tree traversal should be
	 * stopped before visiting all nodes.
	 * Useful for short circuiting traversal
	 * if we already know we are done.
	 *
	 * @return true/false
	 */
	boolean stopTraversal();

	/**
	 * Method that is called to indicate whether
	 * we should skip all nodes below this node
	 * for traversal.  Useful if we want to effectively
	 * ignore/prune all branches under a particular 
	 * node.  
	 * <p>
	 * Differs from stopTraversal() in that it
	 * only affects subtrees, rather than the
	 * entire traversal.
	 *
	 * @param node 	the node to process
	 * 
	 * @return true/false
	 */
	boolean skipChildren(Visitable node);
}	
