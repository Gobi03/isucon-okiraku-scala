package repository

import java.sql.Timestamp

import com.google.inject.{Inject, Singleton}
import model.Tweet
import scalikejdbc._

trait TweetRepository {
  def findOrderByCreatedAtDesc(): Seq[Tweet]
  def findOrderByCreatedAtDesc(until: String): Seq[Tweet]
  def findByUserIdOrderByCreatedAtDesc(userId: Int): Seq[Tweet]
  def findByUserIdOrderByCreatedAtDesc(userId: Int, createdAt: String): Seq[Tweet]
  def create(userId: Int, text: String): Int

  def findOrderByCreatedAtDescIndex(userId: Int, friends: Seq[String], perPage: Int): Seq[Tweet]
  def findOrderByCreatedAtDescIndex(createdAt: String, userId: Int, friends: Seq[String], perPage: Int): Seq[Tweet]
}

@Singleton
class TweetRepositoryImpl @Inject()(appDBConnection: AppDBConnection) extends TweetRepository {

  def convert(rs: WrappedResultSet): Tweet = Tweet(rs.get[Int](2), rs.get[String](3), rs.get[Timestamp](4).toLocalDateTime)

  override def findOrderByCreatedAtDesc(): Seq[Tweet] = {
    appDBConnection.db.localTx{ implicit session =>
      sql"SELECT * FROM tweets ORDER BY created_at DESC"
        .map(rs => convert(rs)).list.apply()
    }
  }

  override def findOrderByCreatedAtDescIndex(userId: Int, friends: Seq[String], perPage: Int): Seq[Tweet] = {
    appDBConnection.db.localTx{ implicit session =>
      sql"""SELECT tweets.* FROM tweets
           INNER JOIN users ON tweets.user_id = ${userId}
           WHERE users.name IN (${friends.map(str => s"'${str}").mkString(",")})
              ORDER BY created_at DESC LIMIT ${perPage}"""
        .map(rs => convert(rs)).list.apply()
    }
  }

  override def findOrderByCreatedAtDesc(createdAt: String): Seq[Tweet] = {
    appDBConnection.db.localTx{ implicit session =>
      sql"SELECT tweets.* FROM tweets WHERE created_at < ${createdAt} ORDER BY created_at DESC"
        .map(rs => convert(rs)).list.apply()
    }
  }

  override def findOrderByCreatedAtDescIndex(createdAt: String, userId: Int, friends: Seq[String], perPage: Int): Seq[Tweet] = {
    appDBConnection.db.localTx{ implicit session =>
      sql"""SELECT * FROM tweets
           INNER JOIN users ON tweets.user_id = ${userId}
           WHERE users.name IN (${friends.map(str => s"'${str}").mkString(",")})
            AND created_at < ${createdAt}
           ORDER BY created_at DESC LIMIT ${perPage}"""
        .map(rs => convert(rs)).list.apply()
    }
  }

  override def findByUserIdOrderByCreatedAtDesc(userId: Int): Seq[Tweet] = {
    appDBConnection.db.localTx{ implicit session =>
      sql"SELECT * FROM tweets WHERE user_id = ${userId} ORDER BY created_at DESC"
        .map(rs => convert(rs)).list.apply()
    }
  }

  override def findByUserIdOrderByCreatedAtDesc(userId: Int, createdAt: String): Seq[Tweet] = {
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
