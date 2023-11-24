package com.seoulfultrip.seoulfultrip
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnTouchListener
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.seoulfultrip.seoulfultrip.MySelectAdapter.Companion.savepname
import com.seoulfultrip.seoulfultrip.PathActivity.Companion.itemList
import com.seoulfultrip.seoulfultrip.PathActivity.Companion.pnamelist
import com.seoulfultrip.seoulfultrip.StartplaceAdapter.Companion.savestname
import com.seoulfultrip.seoulfultrip.databinding.ActivityPathBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class PathActivity : AppCompatActivity() {
    lateinit var binding: ActivityPathBinding
    var startPlace:String? = savestname[0] // 출발지 이름
    lateinit var adapter: MyPathAdapter
   //최종경로 저장
    var durationarray = mutableListOf<Int?>() //시간 저장
    var durationpname :MutableMap<Int?, String?> = mutableMapOf() //시간-이름 저장
    var newsavepname = mutableListOf<String?>() //출발지 빼고 list 새로 저장
    var slatitude: Double? = 0.0 //출발지 위도
    var slongitude: Double? = 0.0//출발지 경도
    var flatitude: Double? = 0.0//도착지 위도
    var flongitude: Double? = 0.0//도착지 경도


    companion object{
        var itemList = mutableListOf<PlaceStorage>()
        var pnamelist= mutableListOf<String?>()
        //var copy=mutableListOf<String?>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPathBinding.inflate(layoutInflater)
        var pathName: String? = null
        pathName = binding.pathName.text.toString()
        setContentView(binding.root)

        setSupportActionBar(binding.Pathtoolbar) // toolbar 사용 선언
        getSupportActionBar()?.setTitle("${pathName}") // 사용자가 설정한 경로 이름으로 변경 (추후 수정)
        getSupportActionBar()?.setDisplayHomeAsUpEnabled(true)

        binding.pathLayout.setOnTouchListener(OnTouchListener { v, event ->
            val inputManager =
                this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputManager.hideSoftInputFromWindow(
                this.currentFocus!!.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
            false
        })

        MyApplication.db.collection("place")
            //정렬 안 함
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val item = document.toObject(PlaceStorage::class.java)
                    item.docId = document.id
                    itemList.add(item)
                    Log.d("d", " ${itemList.size}")
                }
                    //binding.pathRecyclerView.layoutManager = LinearLayoutManager(this)
                    //binding.pathRecyclerView.adapter = MyPathAdapter(this, itemList)


                    //설정한 출발지 빼고 list 새로 생성
                    for (index in 0 until savepname.size) {
                        if (startPlace != savepname.get(index)) {
                            newsavepname.add(savepname[index])
                        }
                    }
                    //최종리스트에 출발지 추가
                    pnamelist.add(startPlace)
                    Log.d("출발지빼고 리스트", " ${newsavepname}")



                    //itemList에서 출발지 위도 경도 가져오기
                    for (index in 0..itemList.size - 1) {
                        val num = itemList.get(index)
                        if (startPlace == num.pname) {
                            slongitude = num.longitude
                            slatitude = num.latitude
                            Log.d("출발지${index}", " ${slongitude},${slatitude}")
                        }
                    }


                    //출발지 제외하고 나머지 위도 경도 받아와서 api로 시간 받아오기
                    for (index in 0..itemList.size - 1) {
                        val num = itemList.get(index)
                        for (index in 0..newsavepname.size - 1) {
                            if (newsavepname[index] == num.pname) {
                                flongitude = num.longitude
                                flatitude = num.latitude
                                Log.d("끝도착${index}", " ${flongitude}, ${flatitude}")
                                //pnamelong.put(num.pname, flongitude)
                                apistart(slongitude, slatitude, flongitude, flatitude, num.pname)

                            }
                        }
                    }


                }

            .addOnFailureListener {
                Log.d("데이터 불러오기", "실패")
            }

        binding.map.setOnClickListener {
            val intent = Intent(this,MapPathActivity::class.java )
            startActivity(intent)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean { // Menu 연걸
        menuInflater.inflate(R.menu.menu_delete, menu)

        // 메뉴 삭제 버튼 비활성화
        val deleteMenuItem = binding.Pathtoolbar.menu.findItem(R.id.delete_button)
        deleteMenuItem.isVisible = false

        val NextMenuItem = binding.Pathtoolbar.menu.findItem(R.id.next1_button)
        NextMenuItem.title = "저장"

        return super.onCreateOptionsMenu(menu)
    }

    // 장소 저장 후 Home 새로고침을 위한 코드 (현재 저장한 경로가 떠야하므로)
    override fun onRestart() {
        super.onRestart()
        HomeFragment().refreshAdapter()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { // 뒤로가기 버튼
                savestname.clear() //화면 넘어가도 배열은 남아있어서 값전달 잘못돼서 배열초기화

            }

            R.id.next1_button -> {  //저장 버튼을 누르면...
                // 생성된 경로 파이어베이스에 저장

                // 홈 프레그먼트로 이동
                val intent = Intent(this,MainActivity::class.java)
                startActivity(intent)
                finish()

            }
        }
        return super.onOptionsItemSelected(item)
    }

    //시간 받아오는 함수
    private fun apistart( slongitude: Double?,slatitude:Double?, flongitude: Double?, flatitude:Double?, pname:String?) {


        var nextpname: String? = ""
        val CLIENT_ID = "ylvy2f6syf"
        val CLIENT_SECRET = "6kWgp5OQ0jqstBFDg1AGMtisZdzUkJy9P6PI57AT"

        //Log.d("출발지값","${slatitude}, ${slongitude}")
        //Log.d("도착지값","${flatitude}, ${flongitude}")

        val retrofit = Retrofit.Builder() //retrofit으로 api받아오기 시작
            .baseUrl("https://naveropenapi.apigw.ntruss.com/map-direction/") //전달할 http
            .addConverterFactory(GsonConverterFactory.create()) //gson
            .build()

        val api = retrofit.create(PathAPI::class.java) ///PathAPI 인터페이스 전달
        //http에 아이디, 키, 시작점의 위도경도, 도착점의 위도경도 전달
        val callGetPath = api.getPath(CLIENT_ID, CLIENT_SECRET, "${slongitude},${slatitude}", "${flongitude},${flatitude}")

        callGetPath.enqueue(object : Callback<PathPlace> { //PathPlace 데이터클래스 콜백
            override fun onResponse(call: Call<PathPlace>, response: Response<PathPlace>
            ) { //전달이 성공하면 여기시작
                val pathlist = response.body()?.route?.traoptimal //데이터클래스에서 Result_trackoption까지 받음, list형식이라 뒤는 따로 받아와야함
                //Log.d("경로", "${pathlist}")
                for (pathdi in pathlist!!) { //pathlist에서 summary.duration받아오기 위해 for문 사용
                    var time = pathdi.summary.duration

                    durationarray.add(time)
                    durationpname.put(time,pname)

                    /*
                    for (index in 0 .. durationarray.size-2) {
                        if (durationarray[index] == time) {
                        }
                        else{ durationarray.add(time) //시간 비교하기 위해 durationarray(mutablelist)에 시간만 모아서 저장
                            durationpname.put(time,pname) }
                    }*/
                   // 시간에 따른 장소이름 출력하기 위해 duraionpname(mutableMap)에 key값은 시간 value값은 장소로 저장
                    Log.d("시간확인", "${time}")
                    Log.d("시간-장소 확인", "${durationpname}")
                    Log.d("시간- 확인", "${durationarray}")

                    if(durationarray.size==newsavepname.size){ //갯수에 맞게 잘 받아왔으면
                        var mintime = timecalculate() //시간 비교->최소시간 가져옴
                        Log.d("최소시간 확인","${mintime}")
                        nextpname = durationpname.get(mintime) //최소시간에 맞는 장소이름 가져오기
                        pnamelist.add(nextpname)//최종리스트에 장소이름 추가
                        //Log.d("장소 확인","${b}")
                        arrayreset(nextpname) //최소시간으로 가져온 거 list에서 제외

                        when (newsavepname.size){
                            0->{ Log.d("최종리스트", "${pnamelist}")
                                listvisible()
                                continue}
                            1->{pnamelist.add(newsavepname[0]) //하나 남은 장소 최종리스트에 추가
                                Log.d("최종리스트", "${pnamelist}")
                                listvisible()
                                continue}
                            else->{pstart(nextpname)} //장소 여러개 남았을 때 아까 구한 최소시간 장소 넘겨주기
                            }
                        }


                } //시간받아옴
            }
            override fun onFailure(call: Call<PathPlace>, t: Throwable) {
                Log.d("실패", "실패")
            }
        })
        //Log.d("거리시간2", "${b}")
        //return time
    }

    private fun timecalculate(): Int? {

        var min = durationarray[0]
        for (index in 1..durationarray.size-1) {
            if(min!! > durationarray[index]!!){
                min=durationarray[index] //최소시간 담기
            }
        }
        durationarray.clear()
        return min


    }



    private fun arrayreset(pname: String?){

        newsavepname.remove(pname)
        Log.d("장소삭제확인", " ${newsavepname}")
    }

    private fun pstart(pname: String?){
        for (index in 0..itemList.size - 1) {
            val num = itemList.get(index)
            if (pname == num.pname) { //전에 구한 최소시간장소가 출발지 장소가 됨
                slongitude = num.longitude
                slatitude = num.latitude
                Log.d("시작도착${index}", " ${slongitude},${slatitude}")
            }
        }
        for (index in 0..itemList.size - 1) {
            val num = itemList.get(index)
            for (index in 0..newsavepname.size - 1) {
                if (newsavepname[index] == num.pname) {
                    flongitude = num.longitude
                    flatitude = num.latitude
                    Log.d("끝도착${index}", " ${flongitude}, ${flatitude}")
                    //pnamelong.put(num.pname, flongitude)
                    Log.d("apistart", "apistart다시")
                    apistart(slongitude, slatitude, flongitude, flatitude, num.pname)

                }
            }
        }

    }

    private fun listvisible(){
        for (index in 0..pnamelist.size-1) { //최종리스트에 있는 장소 갯수만큼 view생성 / 출발지와 그 다음 장소는 필수

            when(index) {
                0 -> {
                    binding.itemNameView1.setText(pnamelist[index])
                    binding.itemNameView1.visibility = View.VISIBLE
                    binding.itemImageView1.visibility = View.VISIBLE
                    binding.itemPathline1.visibility=View.VISIBLE
                }

                1 -> {
                    binding.itemNameView2.setText(pnamelist[index])
                    binding.itemNameView2.visibility = View.VISIBLE
                    binding.itemImageView2.visibility = View.VISIBLE
                    binding.itemPathline1.visibility=View.VISIBLE
                }
            }

            if (pnamelist.get(index)!=null){ //리스트에 장소 없을 시 통과안함
                when(index) {
                    2 -> { binding.itemNameView3.setText(pnamelist[index])
                        binding.itemNameView3.visibility=View.VISIBLE
                        binding.itemImageView3.visibility=View.VISIBLE
                        binding.itemPathline2.visibility=View.VISIBLE}

                    3 -> {binding.itemNameView4.setText(pnamelist[index])
                        binding.itemNameView4.visibility=View.VISIBLE
                        binding.itemImageView4.visibility=View.VISIBLE
                        binding.itemPathline3.visibility=View.VISIBLE}


                    4 -> {binding.itemNameView5.setText(pnamelist[index])
                        binding.itemNameView5.visibility=View.VISIBLE
                        binding.itemImageView5.visibility=View.VISIBLE
                        binding.itemPathline4.visibility=View.VISIBLE}
                }
            }

        }

    }
}

