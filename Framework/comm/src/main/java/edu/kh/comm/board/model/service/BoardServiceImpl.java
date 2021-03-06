package edu.kh.comm.board.model.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.core.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import edu.kh.comm.board.controller.BoardController;
import edu.kh.comm.board.model.dao.BoardDAO;
import edu.kh.comm.board.model.exception.InsertFailException;
import edu.kh.comm.board.model.vo.Board;
import edu.kh.comm.board.model.vo.BoardDetail;
import edu.kh.comm.board.model.vo.BoardImage;
import edu.kh.comm.board.model.vo.BoardType;
import edu.kh.comm.board.model.vo.Pagination;
import edu.kh.comm.common.Util;

@Service // 비지니스 로직을 처리한다는 클래스 명시 + bean 등록
public class BoardServiceImpl implements BoardService {

	@Autowired
	private BoardDAO dao;

	// Logger log = LoggerFactory.getLogger(BoardServiceImpl.class);

	// 게시판 종류 조회
	@Override
	public List<BoardType> selectBoardType() {
		return dao.selectBoardType();
	}

	// 게시글 목록 조회
	@Override
	public Map<String, Object> selectBoardList(int cp, int boardCode) {

		int listCount = dao.getListCount(boardCode);
		Pagination pagination = new Pagination(cp, listCount);

		List<Board> boardList = dao.selectBoardList(pagination, boardCode);

		// map
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("pagination", pagination);
		map.put("boardList", boardList);

		return map;
	}

	/**
	 * 검색된 게시글 목록 조회
	 *
	 */
	@Override
	public Map<String, Object> searchBoardList(Map<String, Object> paramMap) {
		// 검색 조건에 맞는 전체 게시글 개수 조회
		int listCount = dao.searchListCount(paramMap);
		Pagination pagination = new Pagination((int) paramMap.get("cp"), listCount);
		List<Board> boardList = dao.searchBoardList(pagination, paramMap);
		Map<String, Object> map = new HashMap<String, Object>();
		
		map.put("pagination", pagination);
		map.put("boardList", boardList);

		return map;
	}

	// 게시글 상세 조회
	@Override
	public BoardDetail selectBoardDetail(int boardNo) {
		return dao.selectBoardDetail(boardNo);
	}

	// 조회수 증가 기능
	@Override
	public int updateReadCount(int boardNo) {
		return dao.updateReadCount(boardNo);
	}

	/*
	 * // 게시글 작성 + image삽입 구현
	 * 
	 * // Spring에서 트랜잭션 처리하는 방법
	 * 
	 * // * AOP (관점 지향 프로그래밍)을 이용해 DAO -> Service 또는 Service 코드 수행 반환되는 시점에 // 예외가
	 * 발생시 rollback을 수행 (관점지향 ~~할 때 , ~~ 할 경우 )
	 * 
	 * // 방법 1) <tx:advice> xml을 이용한 방식 -> 패턴을 지정하며 일치하는 메서드 호출 시 자동으로 트랜잭션을 제어 //
	 * 너무 빡빡해서 느려짐
	 * 
	 * // 방법 2) @Transactional 어노테이션 활용 - 선언적 트랜잭션 처리 방법. // RunTimeException
	 * (Unchecked Exception) 처리를 기본값으로 같는다
	 * 
	 * // Checked Exception : 예외 처리가 필수 ( transFerTo ) == 외부 요소들 , 발생시 충격강도가 강할때 ,
	 * // SQL관련 예외 , 파일 업로드 관련 예외 // UnChecked Exception : 예외 처리가 선택 ( int a = 10/0;
	 * == 산술적 예외 ) == 사용자의 문제들
	 */
	// rollbackFor : rollback을 수행하기 위한 예외의 종류를 작성한다.
	@Transactional(rollbackFor = { Exception.class })
	@Override
	public int insertBoard(BoardDetail detail, List<MultipartFile> imageList, String webPath, String folderPath)
			throws Exception {
		// 1) 게시글 삽입
		// 1.1) XSS 방지 처리 + 개행문자 처리
		detail.setBoardTitle(Util.XSSHandling(detail.getBoardTitle()));
		// detail.setBoardContent(Util.XSSHandling(detail.getBoardContent()));
		// 게시글 xss 처리먼저 하고 개행도하고 집어넣기 ㅋㅋㅋ 이렇게 해도 되나
		detail.setBoardContent(Util.newLineHandling(Util.XSSHandling(detail.getBoardContent())));

		int boardNo = dao.insertBoard(detail);

		if (boardNo > 0) {
			// 이미지 삽입.

			// imageList : 실제 파일이 담겨있는 리스트 서버에서 넘어온
			// boardImageList : DB 삽입할 이미지 정보만 담겨있는 리스트
			// rename 리스트 변경된 파일명이 담겨있는 리스트

			List<BoardImage> boardImageList = new ArrayList<BoardImage>();
			List<String> reNameList = new ArrayList<String>();

			// size == 5 이미지 올리는 공간이 5칸이라서
			// imageList에 담겨있는 파일 정보 중 실제로 업로드된 파일만 분류하는 작업
			// 비어있는 경우도 있기 때문에.

			for (int i = 0; i < imageList.size(); i++) {

				// i 번째 이미지는 업로드된 이미지가 있을 경우 없으면 0이기 때문
				if (imageList.get(i).getSize() > 0) {

					// 변경된 파일명 저장
					String rename = Util.fileRename(imageList.get(i).getOriginalFilename());
					reNameList.add(rename);

					// BoardImage 객체를 생성하여 값 세팅 후 boardImageList에 추가
					BoardImage img = new BoardImage();
					img.setBoardNo(boardNo); // 게시글 번호
					img.setImageLevel(i); // 이미지 순서
					img.setImageOriginal(imageList.get(i).getOriginalFilename()); // 원본 파일명
					img.setImageReName(webPath + rename); // 웹 경로 + 방금 만든 이름

					boardImageList.add(img);
				} // if문 끝

			} // for문 끝

			// 분류 작성 종료 후 boardImageList가 비어있지 않은 경우 == 삽입된 이미지가 있는 경우.
			// 비어있지 않은 경우
			if (!boardImageList.isEmpty()) {

				int result = dao.insertBoardImageList(boardImageList);
				// result는 삽입 성공한 행의 개수가 된다
				if (boardImageList.size() == result) {// 등록한 사진이랑 업로드한 사진이랑 개수가 동일한 경우.
					// 서버에 이미지를 저장
					for (int i = 0; i < boardImageList.size(); i++) {
						int index = boardImageList.get(i).getImageLevel();
						imageList.get(index).transferTo(new File(folderPath + reNameList.get(i)));
//						imageList.get(index).transferTo(new File(folderPath+boardImageList.get(i).getImageReName()));
					}
				} else {// 이미지 삽입 실패시
						// 사용자가 정의한 예외를 rollback을 수행하게함.
					throw new InsertFailException(); // 매개변수로 작성 가능
				}
			}
		}
		return boardNo;
	}

	// 게시글 삭제
	@Override
	public int deleteBoard(int boardNo) {
		return dao.deleteBoard(boardNo);
	}

	// 게시글 수정
	@Transactional(rollbackFor = { Exception.class }) // 모든종류의 예외 발생 시 현재 서비스 모든 내용 rollback 수행
	@Override
	public int updateBoard(BoardDetail detail, List<MultipartFile> imageList, String webPath, String folderPath,
			String deleteList) throws IOException {

		// XSS , 개행문자
		detail.setBoardTitle(Util.XSSHandling(detail.getBoardTitle()));
		detail.setBoardContent(Util.newLineHandling(Util.XSSHandling(detail.getBoardContent())));

		// 2. 게시글만 수정하는 DAO 호출 (제목,내용,마지막 수정일)만 수정하는 DAO 호출
		int result = dao.updateBoard(detail);

		if (result > 0) { // 성공한 경우
			// 3. 업로드된 이미지만 분류
			List<BoardImage> boardImageList = new ArrayList<BoardImage>();
			List<String> reNameList = new ArrayList<String>();

			for (int i = 0; i < imageList.size(); i++) {
				if (imageList.get(i).getSize() > 0) {
					String rename = Util.fileRename(imageList.get(i).getOriginalFilename());
					reNameList.add(rename);
					BoardImage img = new BoardImage();
					img.setBoardNo(detail.getBoardNo());
					img.setImageLevel(i); // 이미지 순서
					img.setImageOriginal(imageList.get(i).getOriginalFilename());
					img.setImageReName(webPath + rename);
					boardImageList.add(img);
				}
			} // FOR문 종료

			// 4) deleteList를 이용해 이미지 삭제
			if (!deleteList.equals("")) {// 빈칸이 아니라면
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("boardNo", detail.getBoardNo());
				map.put("deleteList", deleteList);
				result = dao.deleteBoardImage(map);
			}

			if (result > 0) {// 삭제한 이미지가 있을경우 우선
				// 5) boardImageList 순차 접근

				for (BoardImage img : boardImageList) { // 해당 컬렉션에 값이 없다면 자동으로 진행되지 않는다.
					result = dao.updateBoardImage(img); // 변경명 , 원복명 , 게시글번호 ,레벨

					// 1 : 수정 성공 (기존 이미지가 있엇다)
					// 2 : 원본 이미지가 없기 때문에 0이 온다

					// 6) update를 실패하면 INSERT로 수행
					if (result == 0) {
						result = dao.insertBoardImage(img);
						// 값을 직접 대입해서 삽입하는 경우 결과가 0이 나올 수가 없다
						// 단, 예외(제약조건 위베 , sql 문법 오류) 등은 발생가능
					}
				} // fOR문 끝

				if (!boardImageList.isEmpty() && result != 0) {
					for (int i = 0; i < boardImageList.size(); i++) {
						// i번째 진짜 입력된 이미지의 레벨을 얻어오기
						int index = boardImageList.get(i).getImageLevel();
						imageList.get(index).transferTo(new File(folderPath + reNameList.get(i)));
					}
				}
			} // if문 끝
		}
		return result;
	}


	/**게시판 이미지 목록들 조회
	 *
	 */
	@Override
	public List<String> selectDbList() {
		return dao.selectDbList();
	}
	
	
	
}
