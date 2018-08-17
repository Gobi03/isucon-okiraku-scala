package repository

import java.sql.Timestamp
import java.time.format.DateTimeFormatter

import com.google.inject.{Inject, Singleton}
import model.Tweet
import scalikejdbc._

trait TweetRepository {
  def findFriend(userId: Int, limit: Int): Seq[Tweet]
  def findFriend(userId: Int, limit: Int, createdAt: String): Seq[Tweet]

  def search(query: String, limit: Int): Seq[Tweet]
  def search(query: String, createdAt: String, limit: Int): Seq[Tweet]

  def findByUserId(userId: Int, limit: Int): Seq[Tweet]
  def findByUserId(userId: Int, createdAt: String, limit: Int): Seq[Tweet]

  def create(userId: Int, text: String): Int
}

@Singleton
class TweetRepositoryImpl @Inject()(appDBConnection: AppDBConnection) extends TweetRepository {

  private[this] def htmlify(text: String): String = {
    if (text == null) return ""
    text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("'", "&apos;").replaceAll("\"", "&quot;").replaceAll("#(\\S+)(\\s|$)", "<a class=\"hashtag\" href=\"/hashtag/$1\">#$1</a>$2")
  }

  def convert(rs: WrappedResultSet): Tweet = {
    val tweet = Tweet(
      userId = rs.get[Int]("user_id"),
      text = rs.get[String]("text"),
      createdAt = rs.get[Timestamp]("created_at").toLocalDateTime,
      userName = rs.get[String]("user_name")
    )
    tweet.copy(
      html = htmlify(tweet.text),
      time = tweet.createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    )
  }

  override def findFriend(userId: Int, limit: Int): Seq[Tweet] =
    appDBConnection.db.localTx { implicit session =>
      sql"SELECT user_id, text, created_at, (SELECT users.name FROM users WHERE users.id = tweets.user_id) AS user_name FROM tweets WHERE EXISTS (SELECT * FROM friends WHERE me = $userId AND friend = user_id) ORDER BY created_at DESC LIMIT $limit"
        .map(convert).list.apply()
    }

  override def findFriend(userId: Int, limit: Int, createdAt: String): Seq[Tweet] =
    appDBConnection.db.localTx { implicit session =>
      sql"SELECT user_id, text, created_at, (SELECT users.name FROM users WHERE users.id = tweets.user_id) AS user_name FROM tweets WHERE created_at < $createdAt AND EXISTS (SELECT * FROM friends WHERE me = $userId AND friend = user_id) ORDER BY created_at DESC LIMIT $limit"
        .map(convert).list.apply()
    }

  override def search(query: String, limit: Int): Seq[Tweet] =
    appDBConnection.db.localTx { implicit session =>
      sql"SELECT user_id, text, created_at, (SELECT users.name FROM users WHERE users.id = tweets.user_id) AS user_name FROM tweets WHERE text like ${s"%$query%"} ORDER BY created_at DESC LIMIT $limit"
        .map(convert).list.apply()
    }

  override def search(query: String, createdAt: String, limit: Int): Seq[Tweet] =
    appDBConnection.db.localTx { implicit session =>
      sql"SELECT user_id, text, created_at, (SELECT users.name FROM users WHERE users.id = tweets.user_id) AS user_name FROM tweets WHERE created_at < $createdAt AND text like ${s"%$query%"} ORDER BY created_at DESC LIMIT $limit"
        .map(convert).list.apply()
    }

  override def findByUserId(userId: Int, limit: Int): Seq[Tweet] = {
    appDBConnection.db.localTx{ implicit session =>
      sql"SELECT user_id, text, created_at, (SELECT users.name FROM users WHERE users.id = tweets.user_id) AS user_name FROM tweets WHERE user_id = $userId ORDER BY created_at DESC LIMIT $limit"
        .map(rs => convert(rs)).list.apply()
    }
  }

  override def findByUserId(userId: Int, createdAt: String, limit: Int): Seq[Tweet] = {
    appDBConnection.db.localTx{ implicit session =>
      sql"SELECT user_id, text, created_at, (SELECT users.name FROM users WHERE users.id = tweets.user_id) AS user_name FROM tweets WHERE user_id = $userId AND created_at < $createdAt ORDER BY created_at DESC LIMIT $limit"
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
