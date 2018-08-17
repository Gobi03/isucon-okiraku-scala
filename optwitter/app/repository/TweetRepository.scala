package repository

import java.sql.Timestamp

import com.google.inject.{Inject, Singleton}
import model.Tweet
import scalikejdbc._

trait TweetRepository {
  def findOrderByCreatedAtDesc(): Seq[Tweet]
  def findOrderByCreatedAtDesc(until: String): Seq[Tweet]
  def findByUserIdOrderByCreatedAtDescOnlyFriendsLimited(userId: Int): Seq[Tweet]
  def findByUserIdOrderByCreatedAtDescOnlyFriendsLimited(userId: Int, createdAt: String): Seq[Tweet]
  def create(userId: Int, text: String): Int

  def findFriendLimitedAtDesc(userId: Int, limit: Int): Seq[Tweet]
  def findFriendLimitedAtDesc(userId: Int, limit: Int, createdAt: String): Seq[Tweet]
}

@Singleton
class TweetRepositoryImpl @Inject()(appDBConnection: AppDBConnection) extends TweetRepository {

  def convert(rs: WrappedResultSet): Tweet = Tweet(rs.get[Int](2), rs.get[String](3), rs.get[Timestamp](4).toLocalDateTime)

  override def findFriendLimitedAtDesc(userId: Int, limit: Int): Seq[Tweet] =
    appDBConnection.db.localTx { implicit session =>
      sql"SELECT * FROM tweets WHERE EXISTS (SELECT * FROM friends WHERE me = $userId AND friend = user_id) ORDER BY created_at DESC LIMIT $limit"
        .map(convert).list.apply()
    }

  override def findFriendLimitedAtDesc(userId: Int, limit: Int, createdAt: String): Seq[Tweet] =
    appDBConnection.db.localTx { implicit session =>
      sql"SELECT * FROM tweets WHERE created_at < $createdAt AND EXISTS (SELECT * FROM friends WHERE me = $userId AND friend = user_id) ORDER BY created_at DESC LIMIT $limit"
        .map(convert).list.apply()
    }

  override def findOrderByCreatedAtDesc(): Seq[Tweet] = {
    appDBConnection.db.localTx{ implicit session =>
      sql"SELECT * FROM tweets ORDER BY created_at DESC"
        .map(rs => convert(rs)).list.apply()
    }
  }

  override def findOrderByCreatedAtDesc(createdAt: String): Seq[Tweet] = {
    appDBConnection.db.localTx{ implicit session =>
      sql"SELECT * FROM tweets WHERE created_at < ${createdAt} ORDER BY created_at DESC"
        .map(rs => convert(rs)).list.apply()
    }
  }

  override def findByUserIdOrderByCreatedAtDescOnlyFriendsLimited(userId: Int): Seq[Tweet] = {
    appDBConnection.db.localTx{ implicit session =>
      sql"SELECT * FROM tweets WHERE user_id = ${userId} ORDER BY created_at DESC"
        .map(rs => convert(rs)).list.apply()
    }
  }

  override def findByUserIdOrderByCreatedAtDescOnlyFriendsLimited(userId: Int, createdAt: String): Seq[Tweet] = {
    appDBConnection.db.localTx{ implicit session =>
      sql"SELECT * FROM tweets WHERE user_id = ${userId} AND created_at < ${createdAt} ORDER BY created_at DESC"
        .map(rs => convert(rs)).list.apply()
    }
  }

  override def create(userId: Int, text: String): Int = {
    appDBConnection.db.localTx{ implicit session =>
      val res = sql"INSERT INTO tweets (user_id, text, created_at) VALUES (${userId}, ${text}, NOW())"
        .updateAndReturnGeneratedKey.apply()
      res.toInt
    }
  }
}
