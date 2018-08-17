package repository

import com.google.inject.{Inject, Singleton}
import scalikejdbc._

trait FriendsRepository {
  def isFriend(myId: Int, friendId: Int): Boolean
  def beFriend(myId: Int, friendId: Int): Unit
  def wasFriend(myId: Int, friendId: Int): Unit
}

@Singleton
class FriendsRepositoryImpl @Inject()(appDBConnection: AppDBConnection) extends FriendsRepository {

  override def isFriend(myId: Int, friendId: Int): Boolean =
    appDBConnection.db.readOnly { implicit session =>
      sql"SELECT * FROM friends WHERE me = $myId AND friend = $friendId"
        .map(identity).single.apply().isDefined
    }

  override def beFriend(myId: Int, friendId: Int): Unit =
    appDBConnection.db.localTx { implicit session =>
      sql"INSERT INTO friends VALUES ($myId, $friendId)"
        .update()
    }

  override def wasFriend(myId: Int, friendId: Int): Unit =
    appDBConnection.db.localTx { implicit session =>
      sql"DELETE FROM friends WHERE me = $myId AND friend = $friendId"
        .update()
    }
}
