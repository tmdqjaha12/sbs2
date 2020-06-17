import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class Main {
	public static void main(String[] args) {
		App app = new App();
		app.start();
	}
}

// Session
// 현재 사용자가 이용중인 정보
// 이 안의 정보는 사용자가 프로그램을 사용할 때 동안은 계속 유지된다.
class Session {
	private Member loginedMember;
	private Board currentBoard;

	public Member getLoginedMember() {
		return loginedMember;
	}

	public void setLoginedMember(Member loginedMember) {
		this.loginedMember = loginedMember;
	}

	public Board getCurrentBoard() {
		return currentBoard;
	}

	public void setCurrentBoard(Board currentBoard) {
		this.currentBoard = currentBoard;
	}

	public boolean isLogined() {
		return loginedMember != null;
	}
}

// DB 커넥션(진짜 DB와의 연결을 담당)
class DBConnection {
	private Connection connection;

	public void connect() {
		String url = "jdbc:mysql://localhost:3306/site5?serverTimezone=UTC";
		String user = "sbsst";
		String password = "sbs123414";
		String driverName = "com.mysql.cj.jdbc.Driver";

		try {
			// ① 로드(카카오 택시에 `com.mysql.cj.jdbc.Driver` 라는 실제 택시 드라이버를 등록)
			// 하지만 개발자는 실제로 `com.mysql.cj.jdbc.Driver`를 다룰 일은 없다.
			// 내부적으로 JDBC가 알아서 다 해주기 때문에 우리는 JDBC의 DriverManager 를 통해서 DB와의 연결을 얻으면 된다.
			Class.forName(driverName);

			// ② 연결
			connection = DriverManager.getConnection(url, user, password);
		} catch (ClassNotFoundException e) {
			// `com.mysql.cj.jdbc.Driver` 라는 클래스가 라이브러리로 추가되지 않았다면 오류발생
			System.out.println("[로드 오류]\n" + e.getStackTrace());
		} catch (SQLException e) {
			// DB접속정보가 틀렸다면 오류발생
			System.out.println("[연결 오류]\n" + e.getStackTrace());
		}
	}

/////////////////////////
	public int selectRowIntValue(String sql) {
		Map<String, Object> row = selectRow(sql);

		for (String key : row.keySet()) {
			Object value = row.get(key);

			if (value instanceof String) {
				return Integer.parseInt((String) value);
			}
			if (value instanceof Long) {
				return (int) (long) value;
			} else {
				return (int) value;
			}
		}

		return -1;
	}

	public String selectRowStringValue(String sql) {
		Map<String, Object> row = selectRow(sql);

		for (String key : row.keySet()) {
			Object value = row.get(key);

			return value + "";
		}

		return "";
	}

	public boolean selectRowBooleanValue(String sql) {
		int rs = selectRowIntValue(sql);

		return rs == 1;
	}

	public Map<String, Object> selectRow(String sql) {
		List<Map<String, Object>> rows = selectRows(sql);

		if (rows.size() > 0) {
			return rows.get(0);
		}

		return null;
	}

/////////////////////////
	public List<Map<String, Object>> selectRows(String sql) {
		// SQL을 적는 문서파일
		Statement statement = null;
		ResultSet rs = null;

		List<Map<String, Object>> rows = new ArrayList<>();

		try {
			statement = connection.createStatement();
			rs = statement.executeQuery(sql);
			// ResultSet 의 MetaData를 가져온다.
			ResultSetMetaData metaData = rs.getMetaData();
			// ResultSet 의 Column의 갯수를 가져온다.
			int columnSize = metaData.getColumnCount();

			// rs의 내용을 돌려준다.
			while (rs.next()) {
				// 내부에서 map을 초기화
				Map<String, Object> row = new HashMap<>();

				for (int columnIndex = 0; columnIndex < columnSize; columnIndex++) {
					String columnName = metaData.getColumnName(columnIndex + 1);
					// map에 값을 입력 map.put(columnName, columnName으로 getString)
					row.put(columnName, rs.getObject(columnName));
				}
				// list에 저장
				rows.add(row);

//				System.out.println(row.toString());
			}
		} catch (SQLException e) {
			System.err.printf("[SELECT 쿼리 오류, %s]\n" + e.getStackTrace() + "\n", sql);
		}

		try {
			if (statement != null) {
				statement.close();
			}

			if (rs != null) {
				rs.close();
			}
		} catch (SQLException e) {
			System.err.println("[SELECT 종료 오류]\n" + e.getStackTrace());
		}

		return rows;
	}

	public int update(String sql) {
		// UPDATE 명령으로 몇개의 데이터가 수정되었는지
		int affectedRows = 0;

		// SQL을 적는 문서파일
		Statement statement = null;

		try {
			statement = connection.createStatement();
			affectedRows = statement.executeUpdate(sql);
		} catch (SQLException e) {
			System.err.printf("[UPDATE 쿼리 오류, %s]\n" + e.getStackTrace() + "\n", sql);
		}

		try {
			if (statement != null) {
				statement.close();
			}
		} catch (SQLException e) {
			System.err.println("[UPDATE 종료 오류]\n" + e.getStackTrace());
		}

		return affectedRows;
	}

	public int insert(String sql) {
		int id = -1;

		// SQL을 적는 문서파일
		Statement statement = null;
		// SQL의 실행결과 보고서
		ResultSet rs = null;

		try {
			statement = connection.createStatement();
			statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
			rs = statement.getGeneratedKeys();
			if (rs.next()) {
				id = rs.getInt(1);
			}
		} catch (SQLException e) {
			System.err.printf("[INSERT 쿼리 오류, %s]\n" + e.getStackTrace() + "\n", sql);
		}

		try {
			if (statement != null) {
				statement.close();
			}

			if (rs != null) {
				rs.close();
			}
		} catch (SQLException e) {
			System.err.println("[INSERT 종료 오류]\n" + e.getStackTrace());
		}
		return id;
	}

	public void close() {
		try {
			if (connection != null) {
				connection.close();
			}
		} catch (SQLException e) {
			System.err.println("[닫기 오류]\n" + e.getStackTrace());
		}
	}
}

// Factory
// 프로그램 전체에서 공유되는 객체 리모콘을 보관하는 클래스

class Factory {
	private static Session session;
	private static DB db;
	private static DBConnection dbConnection;
	private static BuildService buildService;
	private static ArticleService articleService;
	private static ArticleDao articleDao;
	private static MemberService memberService;
	private static MemberDao memberDao;
	private static Scanner scanner;

	public static DBConnection getDBConnection() {
		if (dbConnection == null) {
			dbConnection = new DBConnection();
		}

		return dbConnection;
	}

	public static Session getSession() {
		if (session == null) {
			session = new Session();
		}

		return session;
	}

	public static Scanner getScanner() {
		if (scanner == null) {
			scanner = new Scanner(System.in);
		}

		return scanner;
	}

	public static DB getDB() {
		if (db == null) {
			db = new DB();
		}

		return db;
	}

	public static ArticleService getArticleService() {
		if (articleService == null) {
			articleService = new ArticleService();
		}

		return articleService;
	}

	public static ArticleDao getArticleDao() {
		if (articleDao == null) {
			articleDao = new ArticleDao();
		}

		return articleDao;
	}

	public static MemberService getMemberService() {
		if (memberService == null) {
			memberService = new MemberService();
		}
		return memberService;
	}

	public static MemberDao getMemberDao() {
		if (memberDao == null) {
			memberDao = new MemberDao();
		}

		return memberDao;
	}

	public static BuildService getBuildService() {
		if (buildService == null) {
			buildService = new BuildService();
		}

		return buildService;
	}
}

// App
class App {
	private Map<String, Controller> controllers;

	// 컨트롤러 만들고 한곳에 정리
	// 나중에 컨트롤러 이름으로 쉽게 찾아쓸 수 있게 하려고 Map 사용
	void initControllers() {
		controllers = new HashMap<>();
		controllers.put("build", new BuildController());
		controllers.put("article", new ArticleController());
		controllers.put("member", new MemberController());
	}

	public App() {
		// 컨트롤러 등록
		initControllers();

		Factory.getDBConnection().connect();
		
		//초기 아티클 &보드 만들기
		//이건 그냥 sql에서 직접 설정하는게 맞다.
		
		// 관리자 회원 생성
		Factory.getMemberService().join("admin", "admin", "관리자");

		// 공지사항 게시판 생성
		Factory.getArticleService().makeBoard("공지시항", 1);
		// 자유 게시판 생성
		Factory.getArticleService().makeBoard("자유게시판", 2);

		// 현재 게시판을 1번 게시판으로 선택
		Factory.getSession().setCurrentBoard(Factory.getArticleService().getBoard(1));
		// 임시 : 현재 로그인 된 회원은 1번 회원으로 지정, 이건 나중에 회원가입, 로그인 추가되면 제거해야함
		Factory.getSession().setLoginedMember(Factory.getMemberService().getMember(1));
	}

	public void start() {

		while (true) {
			System.out.printf("명령어 : ");
			String command = Factory.getScanner().nextLine().trim();

			if (command.length() == 0) {
				continue;
			} else if (command.equals("exit")) {
				break;
			}

			Request reqeust = new Request(command);

			if (reqeust.isValidRequest() == false) {
				continue;
			}

			if (controllers.containsKey(reqeust.getControllerName()) == false) {
				continue;
			}

			controllers.get(reqeust.getControllerName()).doAction(reqeust);
		}

		Factory.getDBConnection().close();
		Factory.getScanner().close();
	}
}

// Request
class Request {
	private String requestStr;
	private String controllerName;
	private String actionName;
	private String arg1;
	private String arg2;
	private String arg3;

	boolean isValidRequest() {
		return actionName != null;
	}

	Request(String requestStr) {
		this.requestStr = requestStr;
		String[] requestStrBits = requestStr.split(" ");
		this.controllerName = requestStrBits[0];

		if (requestStrBits.length > 1) {
			this.actionName = requestStrBits[1];
		}

		if (requestStrBits.length > 2) {
			this.arg1 = requestStrBits[2];
		}

		if (requestStrBits.length > 3) {
			this.arg2 = requestStrBits[3];
		}

		if (requestStrBits.length > 4) {
			this.arg3 = requestStrBits[4];
		}
	}

	public String getControllerName() {
		return controllerName;
	}

	public void setControllerName(String controllerName) {
		this.controllerName = controllerName;
	}

	public String getActionName() {
		return actionName;
	}

	public void setActionName(String actionName) {
		this.actionName = actionName;
	}

	public String getArg1() {
		return arg1;
	}

	public void setArg1(String arg1) {
		this.arg1 = arg1;
	}

	public String getArg2() {
		return arg2;
	}

	public void setArg2(String arg2) {
		this.arg2 = arg2;
	}

	public String getArg3() {
		return arg3;
	}

	public void setArg3(String arg3) {
		this.arg3 = arg3;
	}
}

// Controller
abstract class Controller {
	abstract void doAction(Request reqeust);
}

class ArticleController extends Controller {
	private ArticleService articleService;

	ArticleController() {
		articleService = Factory.getArticleService();
	}

	public void doAction(Request reqeust) {
		if (reqeust.getActionName().equals("list")) {
			actionList(reqeust);
		} else if (reqeust.getActionName().equals("write")) {
			actionWrite(reqeust);
		} else if (reqeust.getActionName().equals("modify")) {
			actionModify(reqeust);
		} else if (reqeust.getActionName().equals("delete")) {
			actionDelete(reqeust);
		} else if (reqeust.getActionName().equals("changeBoard")) {
			actionChangeBoard(reqeust);
		} else if (reqeust.getActionName().equals("detail")) {
			actionDetail(reqeust);
		}
	}

	// 게시판 상세보기
	private void actionDetail(Request reqeust) {
		if (reqeust.getArg1() != null) {
			String articleId = reqeust.getArg1();

			Article isArticle = articleService.getArticle(Integer.parseInt(articleId));

			if (isArticle == null) {
				System.out.println("없는글 입니다");
			} else {
				System.out.printf("== {%s 게시판 글} ==\n", articleService.getBoard(isArticle.getBoardId()).getName());
				System.out.printf("제목 : %s\n", isArticle.getTitle());
				System.out.printf("내용 : %s\n", isArticle.getBody());
				System.out.printf("작성시간 : %s\n", isArticle.getRegDate());
			}
		}
	}

	// 게시판 이동
	private void actionChangeBoard(Request reqeust) {
		if (reqeust.getArg1() != null) {
			String boardId = reqeust.getArg1();

			Board isBoard = Factory.getArticleService().moveBoard(boardId);

			if (isBoard == null) {
				System.out.println("존재하지 않는 게시판 입니다.");
			} else {
				Factory.getSession().setCurrentBoard(isBoard);
				System.out.printf("{%s}게시판으로 이동 하였습니다\n", isBoard.getName());
			}
		}
	}

	// 게시물 삭제
	private void actionDelete(Request reqeust) {
		if (reqeust.getArg1() != null) {
			System.out.println("== 게시물 삭제 ==");
			int id = Integer.parseInt(reqeust.getArg1());

			int newId = articleService.delete(id);

			if (newId == 0) {
				System.out.println("없는 글입니다.");
			} else {
				System.out.printf("%d번 글이 삭제되었습니다.\n", newId);
			}
		}
	}

	// 게시물 수정
	private void actionModify(Request reqeust) {
		if (reqeust.getArg1() != null) {
			System.out.println("== 게시물 수정 ==");
			int id = Integer.parseInt(reqeust.getArg1());
			String title = "";
			String body = "";

			while (true) {
				System.out.print("제목 : ");
				title = Factory.getScanner().nextLine();
				if (title.length() == 0) {
					continue;
				}

				break;
			}

			while (true) {
				System.out.print("내용 : ");
				body = Factory.getScanner().nextLine();
				if (body.length() == 0) {
					continue;
				}

				break;
			}

			// 게시물 해당 게시판 id 가져오기
			int boardId = articleService.getArticle(Integer.parseInt(reqeust.getArg1())).getBoardId();

			// 현재 로그인한 회원의 id 가져오기
			int memberId = Factory.getSession().getLoginedMember().getId();
			int newId = articleService.modify(boardId, memberId, title, body, id);

			if (newId == 0) {
				System.out.println("없는 글입니다.");
			} else {
				System.out.printf("%d번 글이 수정되었습니다.\n", newId);
			}
		}
	}

	// 게시물리스트
	private void actionList(Request reqeust) {
		List<Article> articles = articleService.getArticles();

		System.out.println("== 게시물 리스트 시작 ==");
		for (Article article : articles) {
			System.out.printf("%d, %s, %s\n", article.getId(), article.getRegDate(), article.getTitle());
		}
		System.out.println("== 게시물 리스트 끝 ==");
	}

	// 글쓰기
	private void actionWrite(Request reqeust) {
		System.out.printf("제목 : ");
		String title = Factory.getScanner().nextLine();
		System.out.printf("내용 : ");
		String body = Factory.getScanner().nextLine();

		// 현재 게시판 id 가져오기
		int boardId = Factory.getSession().getCurrentBoard().getCode();

		// 현재 로그인한 회원의 id 가져오기
		int memberId = Factory.getSession().getLoginedMember().getId();
		int newId = articleService.write(boardId, memberId, title, body);

		System.out.printf("%d번 글이 생성되었습니다.\n", newId);
	}
}

class BuildController extends Controller {
	private BuildService buildService;

	BuildController() {
		buildService = Factory.getBuildService();
	}

	@Override
	void doAction(Request reqeust) {
		if (reqeust.getActionName().equals("site")) {
			actionSite(reqeust);
		}
	}

	private void actionSite(Request reqeust) {
		buildService.buildSite();
	}
}

class MemberController extends Controller {
	private MemberService memberService;

	MemberController() {
		memberService = Factory.getMemberService();
	}

	void doAction(Request reqeust) {
		if (reqeust.getActionName().equals("logout")) {
			actionLogout(reqeust);
		} else if (reqeust.getActionName().equals("login")) {
			actionLogin(reqeust);
		} else if (reqeust.getActionName().equals("whoami")) {
			actionWhoami(reqeust);
		} else if (reqeust.getActionName().equals("join")) {
			actionJoin(reqeust);
		}
	}

	private void actionJoin(Request reqeust) {

	}

	private void actionWhoami(Request reqeust) {
		Member loginedMember = Factory.getSession().getLoginedMember();

		if (loginedMember == null) {
			System.out.println("나그네");
		} else {
			System.out.println(loginedMember.getName());
		}

	}

	private void actionLogin(Request reqeust) {
		System.out.printf("로그인 아이디 : ");
		String loginId = Factory.getScanner().nextLine().trim();

		System.out.printf("로그인 비번 : ");
		String loginPw = Factory.getScanner().nextLine().trim();

		Member member = memberService.getMemberByLoginIdAndLoginPw(loginId, loginPw);

		if (member == null) {
			System.out.println("일치하는 회원이 없습니다.");
		} else {
			System.out.println(member.getName() + "님 환영합니다.");
			Factory.getSession().setLoginedMember(member);
		}
	}

	private void actionLogout(Request reqeust) {
		Member loginedMember = Factory.getSession().getLoginedMember();

		if (loginedMember != null) {
			Session session = Factory.getSession();
			System.out.println("로그아웃 되었습니다.");
			session.setLoginedMember(null);
		}

	}
}

// Service
class BuildService {
	ArticleService articleService;

	BuildService() {
		articleService = Factory.getArticleService();
	}

	public void buildSite() {
		//계속 덮어씌워지는 현상... site자체를 지우고 다시 만드는 방식도 있지 않을까...
		Util.makeDir("site");
		Util.makeDir("site/article");
		Util.makeDir("site/home");
		Util.makeDir("site/stat");
		Util.makeDir("site/resource");

		String css = Util.getFileContents("site_template/resource/common.css");
		String js = Util.getFileContents("site_template/resource/common.js");
		String log = Util.getFileContents("site_template/resource/log.html");
		String home = Util.getFileContents("site_template/home/index.html");
		String stat = Util.getFileContents("site_template/stat/index.html");
		Util.writeFileContents("site/resource/common.css", css);
		Util.writeFileContents("site/resource/common.js", js);

		String head = Util.getFileContents("site_template/part/head.html");
		String foot = Util.getFileContents("site_template/part/foot.html");

		// 각 게시판 별 게시물리스트 페이지 생성
		List<Board> boards = articleService.getBoards();

		String middle = "";
		for (Board board : boards) {
			String fileLink = board.getCode() + "-list-1.html";
			int fileName = board.getCode();
			middle += "<li><a href=\"../article/" + fileLink + "\">" + fileName + "</a></li>";
		}
		home = home.replace("${LI}", middle);
		stat = stat.replace("${LI}", middle);
		Util.writeFileContents("site/home/index.html", home);
		Util.writeFileContents("site/stat/index.html", stat);

		middle = "";	
		for(Board board : boards) {
			String fileLink = board.getCode() + "-list-1.html";
			int fileName = board.getCode();
			middle += "<li><a href=\"" + fileLink + "\">" + fileName + "</a></li>";
		}
		
		for (Board board : boards) {
			head = Util.getFileContents("site_template/part/head.html");
			foot = Util.getFileContents("site_template/part/foot.html");
			String fileName = board.getCode() + "-list-1.html";

			String html = "";

			List<Article> articles = articleService.getArticlesByBoardCode(board.getCode());

			String template = Util.getFileContents("site_template/article/list.html");

			int count = 0;
			for (Article article : articles) {
//				Member member = memberService.getMember(article.getMemberId());
				
				count++;
				html += "<tr>";
				html += "<td>" + count + "</td>";
//				html += "<td>" + article.getId() + "</td>";
				html += "<td>" + article.getRegDate() + "</td>";
				html += "<td>" + "미설정" + "</td>";
				html += "<td><a href=\"" + article.getId() + ".html\">" + article.getTitle() + "</a></td>";
				html += "</tr>";
			}

			html = template.replace("${TR}", html);
			
			head = head.replace("${LI}", middle);
			
			html = head + html + foot;

			Util.writeFileContents("site/article/" + fileName, html);
		}

		// 게시물 별 파일 생성
		List<Article> articles = articleService.getArticles();

		for (Article article : articles) {
			String html = "";

			html += "<div>제목 : " + article.getTitle() + "</div>";
			html += "<div>내용 : " + article.getBody() + "</div>";
			html += "<div>내용 : " + article.getRegDate() + "</div>";
			html += "<div><a href=\"" + (article.getId() - 1) + ".html\">이전글</a></div>";
			html += "<div><a href=\"" + (article.getId() + 1) + ".html\">다음글</a></div>";

			html = head + html + foot;

			Util.writeFileContents("site/article/" + article.getId() + ".html", html);
		}
	}

}

class ArticleService {
	private ArticleDao articleDao;

	ArticleService() {
		articleDao = Factory.getArticleDao();
	}

	public List<Article> getArticlesByBoardCode(int code) {
		return articleDao.getArticlesByBoardCode(code);
	}

	public List<Board> getBoards() {
		return articleDao.getBoards();
	}

	// 게시판 이동
	public Board moveBoard(String boardId) {
		return articleDao.getBoard(Integer.parseInt(boardId));
	}

	// 게시판 생성
	public void makeBoard(String name, int id) {

		if (articleDao.getBoard(id) == null) {
			Board board = new Board(name, id);
			articleDao.saveBoard(board);
		}

	}

	// id로 board 가져오기
	public Board getBoard(int id) {
		return articleDao.getBoard(id);
	}

	// id로 article 가져오기
	public Article getArticle(int id) {
		return articleDao.getArticle(id);
	}

	// 글삭제
	public int delete(int id) {
		return articleDao.delete(id);
	}

	// 글수정
	public int modify(int boardId, int memberId, String title, String body, int id) {
		Article article = new Article(boardId, memberId, title, body);
		article.setId(id);
		return articleDao.modify(article);
	}

	// 글쓰기
	public int write(int boardId, int memberId, String title, String body) {
		Article article = new Article(boardId, memberId, title, body);
		return articleDao.save(article);
	}

	// 글리스트
	public List<Article> getArticles() {
		return articleDao.getArticles();
	}

}

class MemberService {
	private MemberDao memberDao;

	MemberService() {
		memberDao = Factory.getMemberDao();
	}

	public Member getMemberByLoginIdAndLoginPw(String loginId, String loginPw) {
		return memberDao.getMemberByLoginIdAndLoginPw(loginId, loginPw);
	}

	public int join(String loginId, String loginPw, String name) {
		Member oldMember = memberDao.getMemberByLoginId(loginId);

		if (oldMember != null) {
			return -1;
		}

		Member member = new Member(loginId, loginPw, name);
		return memberDao.save(member);
	}

	public Member getMember(int id) {
		return memberDao.getMember(id);
	}
}

// Dao
class ArticleDao {
	DB db;
	DBConnection dbConnection;

	ArticleDao() {
		db = Factory.getDB(); // 나중에 없어질
		dbConnection = Factory.getDBConnection();
	}

	public List<Article> getArticlesByBoardCode(int id) {
		String sql = "SELECT * FROM article WHERE boardId = " + id + " ORDER BY boardId DESC";
		List<Map<String, Object>> rows = dbConnection.selectRows(sql);

		List<Article> articles = new ArrayList<>();

		for (Map<String, Object> row : rows) {
			articles.add(new Article(row));
		}

		return articles;
	}

	public List<Board> getBoards() {
		List<Map<String, Object>> rows = dbConnection.selectRows("SELECT * FROM board ORDER by boardId DESC");
		List<Board> boards = new ArrayList<>();

		for (Map<String, Object> row : rows) {
			boards.add(new Board(row));
		}

		return boards;
	}

	// return db.getArticles();

//	public Board getBoardByCode(String code) {
//		
//		return db.getBoardByCode(code);
//	}

	// 게시판 생성
	public int saveBoard(Board board) {
		String sql = "";
		sql += "INSERT INTO board ";
		sql += String.format("SET boardId = '%d'", board.getCode());
		sql += String.format(", boardName = '%s'", board.getName());

		return dbConnection.insert(sql);
	}

	// 글 삭제
	public int delete(int id) {
		String sql = "";
		sql += "DELETE FROM article ";
		sql += String.format("WHERE id = '%d'", id);
		if (dbConnection.update(sql) == 0) {
			return 0;
		}
		return id;
	}

	// 수정 저장
	public int modify(Article article) {
		String sql = "";
		sql += "UPDATE article ";
		sql += String.format("SET regDate = '%s'", article.getRegDate());
		sql += String.format(", title = '%s'", article.getTitle());
		sql += String.format(", `body` = '%s'", article.getBody());
		sql += String.format(", memberId = '%d'", article.getMemberId());
		sql += String.format(", boardId = '%d'", article.getBoardId());
		sql += String.format(" WHERE id = '%d'", article.getId());
		if (dbConnection.update(sql) == 0) {
			return 0;
		}
		return article.getId();
	}

	// 새글 저장
	public int save(Article article) {
		String sql = "";
		sql += "INSERT INTO article ";
		sql += String.format("SET regDate = '%s'", article.getRegDate());
		sql += String.format(", title = '%s'", article.getTitle());
		sql += String.format(", `body` = '%s'", article.getBody());
		sql += String.format(", memberId = '%d'", article.getMemberId());
		sql += String.format(", boardId = '%d'", article.getBoardId());

		return dbConnection.insert(sql);
	}

	// id로 board 가져오기
	public Board getBoard(int id) {
		String sql = "SELECT * FROM board WHERE boardId = " + id;
		if (dbConnection.selectRow(sql) != null) {
			Board board = new Board(dbConnection.selectRow(sql));
			return board;
		}
		return null;
	}

	// id로 article 가져오기
	public Article getArticle(int id) {
		String sql = "SELECT * FROM article WHERE id = " + id;
		if (dbConnection.selectRow(sql) != null) {
			Article article = new Article(dbConnection.selectRow(sql));
			return article;
		}
		return null;
	}

	// 게시물리스트
	public List<Article> getArticles() {
		List<Map<String, Object>> rows = dbConnection.selectRows("SELECT * FROM article ORDER by id DESC");
		List<Article> articles = new ArrayList<>();

		for (Map<String, Object> row : rows) {
			articles.add(new Article(row));
		}

		return articles;

		// return db.getArticles();
	}

}

class MemberDao {
	DB db;

	MemberDao() {
		db = Factory.getDB();
	}

	public Member getMemberByLoginIdAndLoginPw(String loginId, String loginPw) {
		return db.getMemberByLoginIdAndLoginPw(loginId, loginPw);
	}

	public Member getMemberByLoginId(String loginId) {
		return db.getMemberByLoginId(loginId);
	}

	public Member getMember(int id) {
		return db.getMember(id);
	}

	public int save(Member member) {
		return db.saveMember(member);
	}
}

// DB
class DB {
	private Map<String, Table> tables;

	public DB() {
		String dbDirPath = getDirPath();
		Util.makeDir(dbDirPath);

		tables = new HashMap<>();

		tables.put("article", new Table<Article>(Article.class, dbDirPath));
		tables.put("board", new Table<Board>(Board.class, dbDirPath));
		tables.put("member", new Table<Member>(Member.class, dbDirPath));
	}

//	public List<Article> getArticlesByBoardCode(String code) {
//		Board board = getBoardByCode(code);
//		// free => 2
//		// notice => 1
//
//		List<Article> articles = getArticles();
//		List<Article> newArticles = new ArrayList<>();
//
//		for (Article article : articles) {
//			if (article.getBoardId() == board.getId()) {
//				newArticles.add(article);
//			}
//		}
//
//		return newArticles;
//	}

	public Member getMemberByLoginIdAndLoginPw(String loginId, String loginPw) {
		List<Member> members = getMembers();

		for (Member member : members) {
			if (member.getLoginId().equals(loginId) && member.getLoginPw().equals(loginPw)) {
				return member;
			}
		}

		return null;
	}

	public Member getMemberByLoginId(String loginId) {
		List<Member> members = getMembers();

		for (Member member : members) {
			if (member.getLoginId().equals(loginId)) {
				return member;
			}
		}

		return null;
	}

	public List<Member> getMembers() {
		return tables.get("member").getRows();
	}

//	public Board getBoardByCode(String code) {
//		List<Board> boards = getBoards();
//		
//
//		for (Board board : boards) {
//			if (board.getCode().equals(code)) {
//				return board;
//			}
//		}
//
//		return null;
//	}

	public List<Board> getBoards() {
		return tables.get("board").getRows();
	}

	public Member getMember(int id) {
		return (Member) tables.get("member").getRow(id);
	}

	public int saveBoard(Board board) {
		return tables.get("board").saveRow(board);
	}

	public String getDirPath() {
		return "db";
	}

	public int saveMember(Member member) {
		return tables.get("member").saveRow(member);
	}

	public Board getBoard(int id) {
		return (Board) tables.get("board").getRow(id);
	}

	public List<Article> getArticles() {
		return tables.get("article").getRows();
	}

	public int saveArticle(Article article) {
		return tables.get("article").saveRow(article);
	}

	public void backup() {
		for (String tableName : tables.keySet()) {
			Table table = tables.get(tableName);
			table.backup();
		}
	}
}

// Table
class Table<T> {
	private Class<T> dataCls;
	private String tableName;
	private String tableDirPath;

	public Table(Class<T> dataCls, String dbDirPath) {
		this.dataCls = dataCls;
		this.tableName = Util.lcfirst(dataCls.getCanonicalName());
		this.tableDirPath = dbDirPath + "/" + this.tableName;

		Util.makeDir(tableDirPath);
	}

	private String getTableName() {
		return tableName;
	}

	public int saveRow(T data) {
		Dto dto = (Dto) data;

		if (dto.getId() == 0) {
			int lastId = getLastId();
			int newId = lastId + 1;
			dto.setId(newId);
			setLastId(newId);
		}

		String rowFilePath = getRowFilePath(dto.getId());

		Util.writeJsonFile(rowFilePath, data);

		return dto.getId();
	};

	private String getRowFilePath(int id) {
		return tableDirPath + "/" + id + ".json";
	}

	private void setLastId(int lastId) {
		String filePath = getLastIdFilePath();
		Util.writeFileContents(filePath, lastId);
	}

	private int getLastId() {
		String filePath = getLastIdFilePath();

		if (Util.isFileExists(filePath) == false) {
			int lastId = 0;
			Util.writeFileContents(filePath, lastId);
			return lastId;
		}

		return Integer.parseInt(Util.getFileContents(filePath));
	}

	private String getLastIdFilePath() {
		return this.tableDirPath + "/lastId.txt";
	}

	public T getRow(int id) {
		return (T) Util.getObjectFromJson(getRowFilePath(id), dataCls);
	}

	public void backup() {

	}

	void delete(int id) {
		/* 구현 */
	};

	List<T> getRows() {
		int lastId = getLastId();

		List<T> rows = new ArrayList<>();

		for (int id = 1; id <= lastId; id++) {
			T row = getRow(id);

			if (row != null) {
				rows.add(row);
			}
		}

		return rows;
	};
}

// DTO
abstract class Dto {
	private int id;
	private String regDate;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getRegDate() {
		return regDate;
	}

	public void setRegDate(String regDate) {
		this.regDate = regDate;
	}

	Dto() {
		this(0);
	}

	Dto(int id) {
		this(id, Util.getNowDateStr());
	}

	Dto(int id, String regDate) {
		this.id = id;
		this.regDate = regDate;
	}
}

class Board extends Dto {
	private String name;
	private int id;

	public Board() {
	}

	public Board(String name, int id) {
		this.name = name;
		this.id = id;
	}

	public Board(Map<String, Object> row) {
		this.setName((String) row.get("boardName"));
		this.setCode((int) (long) row.get("boardId"));
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getCode() {
		return id;
	}

	public void setCode(int id) {
		this.id = id;
	}

}

class Article extends Dto {
	private int boardId;
	private int memberId;
	private String title;
	private String body;

	public Article() {

	}

	public Article(int boardId, int memberId, String title, String body) {
		this.boardId = boardId;
		this.memberId = memberId;
		this.title = title;
		this.body = body;
	}

	public Article(Map<String, Object> row) {
		this.setId((int) (long) row.get("id"));

		String regDate = row.get("regDate") + "";
		this.setRegDate(regDate);
		this.setTitle((String) row.get("title"));
		this.setBody((String) row.get("body"));
		this.setMemberId((int) (long) row.get("memberId"));
		this.setBoardId((int) (long) row.get("boardId"));
	}

	public int getBoardId() {
		return boardId;
	}

	public void setBoardId(int boardId) {
		this.boardId = boardId;
	}

	public int getMemberId() {
		return memberId;
	}

	public void setMemberId(int memberId) {
		this.memberId = memberId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	@Override
	public String toString() {
		return "Article [boardId=" + boardId + ", memberId=" + memberId + ", title=" + title + ", body=" + body
				+ ", getId()=" + getId() + ", getRegDate()=" + getRegDate() + "]";
	}

}

class ArticleReply extends Dto {
	private int id;
	private String regDate;
	private int articleId;
	private int memberId;
	private String body;

	ArticleReply() {

	}

	public int getArticleId() {
		return articleId;
	}

	public void setArticleId(int articleId) {
		this.articleId = articleId;
	}

	public int getMemberId() {
		return memberId;
	}

	public void setMemberId(int memberId) {
		this.memberId = memberId;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

}

class Member extends Dto {
	private String loginId;
	private String loginPw;
	private String name;

	public Member() {

	}

	public Member(String loginId, String loginPw, String name) {
		this.loginId = loginId;
		this.loginPw = loginPw;
		this.name = name;
	}

	public String getLoginId() {
		return loginId;
	}

	public void setLoginId(String loginId) {
		this.loginId = loginId;
	}

	public String getLoginPw() {
		return loginPw;
	}

	public void setLoginPw(String loginPw) {
		this.loginPw = loginPw;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}

// Util
class Util {
	// 현재날짜문장
	public static String getNowDateStr() {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat Date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String dateStr = Date.format(cal.getTime());
		return dateStr;
	}

	// 파일에 내용쓰기
	public static void writeFileContents(String filePath, int data) {
		writeFileContents(filePath, data + "");
	}

	// 첫 문자 소문자화
	public static String lcfirst(String str) {
		String newStr = "";
		newStr += str.charAt(0);
		newStr = newStr.toLowerCase();

		return newStr + str.substring(1);
	}

	// 파일이 존재하는지
	public static boolean isFileExists(String filePath) {
		File f = new File(filePath);
		if (f.isFile()) {
			return true;
		}

		return false;
	}

	// 파일내용 읽어오기
	public static String getFileContents(String filePath) {
		String rs = null;
		try {
			// 바이트 단위로 파일읽기
			FileInputStream fileStream = null; // 파일 스트림

			fileStream = new FileInputStream(filePath);// 파일 스트림 생성
			// 버퍼 선언
			byte[] readBuffer = new byte[fileStream.available()];
			while (fileStream.read(readBuffer) != -1) {
			}

			rs = new String(readBuffer);

			fileStream.close(); // 스트림 닫기
		} catch (Exception e) {
			e.getStackTrace();
		}

		return rs;
	}

	// 파일 쓰기
	public static void writeFileContents(String filePath, String contents) {
		BufferedOutputStream bs = null;
		try {
			bs = new BufferedOutputStream(new FileOutputStream(filePath));
			bs.write(contents.getBytes()); // Byte형으로만 넣을 수 있음
		} catch (Exception e) {
			e.getStackTrace();
		} finally {
			try {
				bs.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// Json안에 있는 내용을 가져오기
	public static Object getObjectFromJson(String filePath, Class cls) {
		ObjectMapper om = new ObjectMapper();
		Object obj = null;
		try {
			obj = om.readValue(new File(filePath), cls);
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {

		} catch (IOException e) {
			e.printStackTrace();
		}

		return obj;
	}

	public static void writeJsonFile(String filePath, Object obj) {
		ObjectMapper om = new ObjectMapper();
		try {
			om.writeValue(new File(filePath), obj);
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void makeDir(String dirPath) {
		File dir = new File(dirPath);
		if (!dir.exists()) {
			dir.mkdir();
		}
	}
}