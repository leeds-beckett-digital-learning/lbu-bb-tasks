/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbtasks.xythos;

import blackboard.data.content.Content;
import blackboard.db.ConnectionNotAvailableException;
import blackboard.persist.BbPersistenceManager;
import blackboard.persist.DatabaseContainer;
import blackboard.persist.Id;
import blackboard.persist.KeyNotFoundException;
import blackboard.persist.PersistenceException;
import blackboard.persist.impl.Bb5Util;
import blackboard.persist.impl.SelectQuery;
import blackboard.platform.persistence.PersistenceServiceFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 *
 * @author jon
 */
public class ContentIdLoader
{
  
  public static Id loadContentIdByFileId(final Id id) throws KeyNotFoundException, PersistenceException, SQLException, ConnectionNotAvailableException
  {
    final BbPersistenceManager pm = PersistenceServiceFactory.getInstance().getDbPersistenceManager();
    
    final LoadQuery query = new LoadQuery( id );
    query.init( ((DatabaseContainer)pm.getContainer()).getBbDatabase(), pm.getContainer() );
    query.executeQuery( null );
    return query.getCourseContentsPk();
  }
  
  
  
  private static class LoadQuery extends SelectQuery
  {
    private final Id _id;
    private Id courseContentsPk = null;

    public LoadQuery(final Id id)
    {
      this._id = id;
    }

    public Id getCourseContentsPk() {
      return courseContentsPk;
    }
    
    @Override
    protected void processRow(final ResultSet rst) throws SQLException, PersistenceException
    {
      if ( courseContentsPk == null )
      {
        long ccpk1 = rst.getLong( 2 );
        courseContentsPk = Id.toId( Content.DATA_TYPE, ccpk1 );
      }
    }

    @Override
    protected Statement prepareStatement(final Connection con) throws KeyNotFoundException, SQLException {
      this._id.assertIsSet();
      final StringBuilder sql = new StringBuilder();
      sql.append("SELECT files_pk1, course_contents_pk1 ");
      sql.append(" FROM  course_contents_files ccf ");
      sql.append(" WHERE files_pk1 = ? ");
      final PreparedStatement stmt = con.prepareStatement(sql.toString());
      Bb5Util.setId(stmt, 1, this._id);
      return stmt;
    }
  }
    
    
}
