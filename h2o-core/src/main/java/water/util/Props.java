package water.util;

import water.util.fp.FP;
import water.util.fp.Function;
import water.util.fp.PartialFunction;
import water.util.fp.Predicate;

import java.util.*;
import static water.util.fp.FP.*;
import static water.util.ArrayUtils.*;

/**
 * LDAP-like storage of properties with multilevel (dot-separated) keys
 * 
 * Backported from scalakittens.Props
 * 
 * Created by vpatryshev on 4/10/17.
 */
public class Props extends PartialFunction<String, String> {
  private Map<String, String> innerMap;
  
  private Props(Map<String, String> innerMap) {
    this.innerMap = innerMap;
  }
  
  static Props props(Map<String, String> innerMap) {
    return new Props(innerMap);
  }
  
  public Props filterValues(Predicate<String> p) {
    Map<String, String> out = new HashMap<>();
    for (Map.Entry<String, String> e : innerMap.entrySet()) {
      if (p.apply(e.getValue())) out.put(e.getKey(), e.getValue());
    }
    return props(out);
  }

  public Props filterKeys(Predicate<String> p) {
    Map<String, String> out = new HashMap<>();
    for (Map.Entry<String, String> e : innerMap.entrySet()) {
      if (p.apply(e.getKey())) out.put(e.getKey(), e.getValue());
    }
    return props(out);
  }
  
  private static String[] keyAsArray(String key) {
    return key.split("\\.");
  }

  private static List<String> keyAsList(String key) {
    return new ArrayList<String>(Arrays.asList(key.split("\\.")));
  }
  
  private static Set<String> keyAsSet(String key) {
    return toSet(keyAsArray(key));
  }
  
  public boolean keyMatches(String suggested, String present) {
    String slk = suggested.toLowerCase();
    String plk = present.toLowerCase();
    return plk.equals(slk) || looseMatch(slk, plk);
  }

  private boolean looseMatch(String suggested, String present) {
    String[] ourKeys = keyAsArray(present);
    String[] suggestedKeys = keyAsArray(suggested);
    int ourPtr = 0;
    for (String element : suggestedKeys) {
      while (ourPtr++ < ourKeys.length) {
        if (ourKeys[ourPtr].matches(element)) break;
      }
      if (ourPtr > ourKeys.length || !ourKeys[ourPtr-1].matches(element)) return false;
    }
    return true;
  }
  
  static boolean keyContains(String suggested, String present) {
    String slk = suggested.toLowerCase();
    String plk = present.toLowerCase();
    if (slk.equals(plk)) return true;
    
    String[] sks = keyAsArray(slk);
    String[] pks = keyAsArray(slk);
    if (sks.length != pks.length) return false;
    
    for (int i = 0; i < sks.length; i++) {
      if (!pks[i].contains(sks[i])) return false;
    }
    
    return true;
  }
  
  private Predicate<String> containsSubkey(final String subkey) {
    return new Predicate<String>() {
      @Override public Boolean apply(String key) {
        return keyAsSet(key).contains(subkey);
      }
    };
  }

  Set<String> matchingKeys(String key) {
    Set<String> s = new HashSet<String>();
    for (String k : innerMap.keySet()) if (keyMatches(key, k)) s.add(k);
    return s;
  }

  @SuppressWarnings("incomplete-switch")
  public Option<String> findKey(String key) {
    if (innerMap.keySet().contains(key))                        return Some(key);
    for (String k : innerMap.keySet()) if (keyMatches(key, k))  return Some(k);
    for (String k : innerMap.keySet()) if (keyContains(key, k)) return Some(k);
    return none();    
  }
  
  public Props findAllHaving(String key) {
    return filterKeys(containsSubkey(key));
  }
  
  public Props findAndReplace(String key, String value) {
    Map<String, String> newOne = new HashMap<>();
    newOne.putAll(innerMap);
    
    Props found = findAllHaving(key);
    for (String k : found.innerMap.keySet()) { newOne.put(k, value); }
    
    return props(newOne);
  }

  private static boolean containsKeyBundle(String suggested, String present) {
    String slk = suggested.toLowerCase();
    String plk = present.toLowerCase();
    if (slk.equals(plk)) return true;
    Set<String> pks = keyAsSet(plk);

    for (String sk : keyAsArray(slk)) {
      if (!pks.contains(sk.replaceAll("\\?", "."))) return false;
    }
    return true;
  }
  
  private PartialFunction<String, String> ForKey = new PartialFunction<String, String>() {
    @Override public Option<String> apply(String s) {
      return Option(innerMap.get(s));
    }
  };
  
  @Override
  public Option<String> apply(String s) {
    return findKey(s).flatMap(ForKey);
  }
  
  public Option<String> forSomeKey(String... collectionOfKeys) {
    for (String key : collectionOfKeys) 
      if (innerMap.containsKey(key)) 
        return Option(innerMap.get(key));
        
    return none();    
  }

  @SuppressWarnings("incomplete-switch")
  public Option<String> findIgnoringKeyOrder(String key) {
    for (String k : innerMap.keySet()) 
      if (containsKeyBundle(key, k)) 
        return apply(k);
    
    return none();
  }
  
  public boolean isDefinedAt(String key) {
    return innerMap.containsKey(key);
  }
  
  public Option<String> get(String key) { return apply(key); }
  
  public Option<String> oneOf(String... keys) {
    for (String k : keys) if (isDefinedAt(k)) return apply(k);
    
    return none();
  }
  
  public String applyOrElse(String key, Function<String, String> otherwise) {
    String fromMap = innerMap.get(key);
    return fromMap != null ? fromMap : otherwise.apply(key);
  }

  public String getOrElse(String key, String otherwise) {
    String fromMap = innerMap.get(key);
    return fromMap != null ? fromMap : otherwise;
  }
  
  public boolean  isEmpty() { return  innerMap.isEmpty(); }
  public boolean nonEmpty() { return !innerMap.isEmpty(); }

  private static List<String> dropIndexKeysInSequence(String[] keys) {
    List<String> out = new LinkedList<>();
    for (String k : keys) if (!ArrayUtils.isInt(k) && !k.isEmpty()) out.add(k);
    return out;
  }

  private static Map<String, String> dropIndexKeys(Map<String, String> m) { 
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<String, String> kv : m.entrySet()) {
      String[] ks = keyAsArray(kv.getKey());
      List<String> cleanedUp = dropIndexKeysInSequence(ks);
      if (!cleanedUp.isEmpty()) {
        String newKey = StringUtils.join(".", cleanedUp);
        result.put(newKey, kv.getValue());
      }
    }
    return result;
  }
  
  public Props dropIndexes() {
    return props(dropIndexKeys(innerMap));
  }
  
  public String toFormattedString() { return formatted(0); }

  protected String formatted(int pad) {
    return spaces(pad) + (
    isEmpty()? "Empty()" : "fp(\n" + formatPf(pad + 2) + "\n" + spaces(pad)
    )+")";
  }

  private String spaces(int n) { return StringUtils.repeat(" ", n); }
  private String escape(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      sb.append(c < 128 ? (""+c) : String.format("\\u%04x", (int)c));
    }
    return sb.toString();
  }
  
  private String formatPf(int pad) {
    String header = "\n" + spaces(pad);
    String[] keys = innerMap.keySet().toArray(new String[innerMap.size()]);
    Arrays.sort(keys);
    StringBuilder sb = new StringBuilder();
    for (String k : keys) {
      if (sb.length() > 0) sb.append(header);
      sb.append("\"").append(escape(k)).append("\"->\"").append(escape(innerMap.get(k))).append("\"");
    }
    
    return "Map(" + sb + ")";
  }

}
/*
import java.util.Date
import scala.io.Source
import scala.util.parsing.combinator.RegexParsers

private def quote(s:String) = Q+escape(s)+Q

    val Q = "\""

private def toStringPf = {
    val keysAndValues = innerMap.map(kv ⇒ quote(kv._1) + " → " + quote(kv._2)).toList.sorted

    "Map(" + keysAndValues.mkString(", ") + ")"
    }

    override def toString = if (isEmpty) "Empty()" else s"fp($toStringPf)"

    override def equals(other:Any) = other match {
    case props: Props ⇒ props.innerMap == self.innerMap
    case _ ⇒ false
    }

    def getOr(key: Any, onError: Props ⇒ String) = Result(pfOpt("" + key), {
    onError(self)
    })

    def hasKey(key: String) = findKey(key).isDefined

    def @@ (key: Any) = getOr(key, props ⇒ s"Missing '$key' in $props")
    def @@ (k1: Any, k2: Any)     : Result[String] = @@ (""+k1+"."+k2)
def @@ (k1: Any, k2: Any, k3: Any) : Result[String] = @@ (""+k1+"."+k2+"."+k3)
def @@ (k1: Any, k2: Any, k3: Any, k4: Any): Result[String] = @@ (""+k1+"."+k2+"."+k3+"."+k4)
def @@ (key: (Any, Any))          : Result[String] = @@ (""+key._1+"."+key._2)
def @@ (key: (Any, Any, Any))     : Result[String] = @@ (""+key._1+"."+key._2+"."+key._3)
def @@ (key: (Any, Any, Any, Any)): Result[String] = @@ (""+key._1+"."+key._2+"."+key._3+"."+key._4)
def @?(key: String)(implicit defaultT: String) = Good(get(key).getOrElse(defaultT))

    def valueOf (key: Any)                 : Result[String] = @@(key)
def valueOf (k1: Any, k2: Any)          : Result[String] = @@(k1,k2)
def valueOf (k1: Any, k2: Any, k3: Any)     : Result[String] = @@(k1,k2,k3)
def valueOf (k1: Any, k2: Any, k3: Any, k4: Any): Result[String] = @@(k1,k2,k3,k4)
def valuesOf(key1:Any, key2:Any): Result[(String, String)] = valueOf(key1) andAlso valueOf(key2)
    def valuesOf(key1:Any, key2:Any, key3:Any): Result[(String, String, String)] =
    Result.zip(valueOf(key1), valueOf(key2), valueOf(key3))
    def valuesOf(key1:Any, key2:Any, key3:Any, key4:Any): Result[(String, String, String, String)] =
    Result.zip(valueOf(key1), valueOf(key2), valueOf(key3), valueOf(key4))
    def valuesOf(key1:Any, key2:Any, key3:Any, key4:Any, key5:Any): Result[(String, String, String, String, String)] =
    Result.zip(valueOf(key1), valueOf(key2), valueOf(key3), valueOf(key4), valueOf(key5))
    def valuesOf(key1:Any, key2:Any, key3:Any, key4:Any, key5:Any, key6:Any): Result[(String, String, String, String, String, String)] =
    Result.zip(valueOf(key1), valueOf(key2), valueOf(key3), valueOf(key4), valueOf(key5), valueOf(key6))


    def guaranteedValueOf(key: String)(implicit defaultT: String) = @?(key)(defaultT)
    def intValueOf(key: Any): Result[Int] = @@(key) map(_.toInt)
//  import DateAndTime._
// TODO(vlad): implement date extraction some time in the future
    //  def dateAt(key: Any): Result[Date] =  @@(key) flatMap extractDate

    def translateBack(dictionary:Traversable[(String, String)]):Props = if (isEmpty) this else {
    val mapper = dictionary.toMap withDefault identity
    def remap(key: String) = {
    keyAsArray(key) map mapper mkString "."
    }
    Props(innerMap map { case(k,v) ⇒ remap(k) → v })
    }

    def translate(dictionary:Traversable[(String, String)]):Props = if (isEmpty) this else {
    translateBack(dictionary.map(_.swap))
    }

    def translateBack(dictionary: Props):Props = translateBack(dictionary.innerMap)

    def translate(dictionary: Props):Props = translate(dictionary.innerMap)

private def subtreeOfMap(map:PropMap, key: String):PropMap = {
    val subtreeOnThisLevel: PropMap = map.filterKeys(_.startsWith(key + ".")).map(kv ⇒ (kv._1.substring(key.length + 1), kv._2))
    val downOneLevel = dropPrefixInMap(map)
    val newMap = subtreeOnThisLevel ++ (if(downOneLevel.isEmpty) downOneLevel else subtreeOfMap(downOneLevel, key))
    newMap
    }

    def subtree(key: String) = if (key.isEmpty) this else {
    Props(subtreeOfMap(innerMap, key))
    }

private def dropPrefixInMap(map:PropMap) = {
    val mapWhereKeysAreSplitIntoPrefixAndTheRest = map map(kv ⇒ (kv._1.split("\\.", 2), kv._2))
    val mapWithDroppedPrefix = mapWhereKeysAreSplitIntoPrefixAndTheRest map (kv ⇒ (kv._1 drop 1 mkString, kv._2))
    val mapWithNonemptyKeys  = mapWithDroppedPrefix filter (kv ⇒ !kv._1.isEmpty)
    mapWithNonemptyKeys
    }

    def dropPrefix:Props = Props(dropPrefixInMap(innerMap))

    def dropAllPrefixes = Props({
    val withSplitKeys = innerMap.map(kv ⇒ (kv._1.split('.'), kv._2))
    val withGoodKeys = withSplitKeys.filter(kv ⇒ kv._1.nonEmpty)
    val withNewKeys =withGoodKeys.map(kv ⇒ (kv._1.last, kv._2))
    withNewKeys
    })

    def keySet:Set[String] = fullKeys map (_.split("\\.", 2)(0))

    def fullKeys:Set[String] = innerMap.keySet

    def commonKeys(other:Props) = fullKeys intersect other.fullKeys

    def trimPrefixes:Props =if (keySet.size == 1  && innerMap.keys.head.contains(".")) dropPrefix.trimPrefixes else self

    def findAllHaving(key: String) = filterKeys (key subsetOf _)

    def filterKeys(predicate: String ⇒ Boolean) = {
    val subMap = innerMap filterKeys predicate
    Props(subMap)
    }

    def findHaving(key:String):Result[String] = {
    val all = findAllHaving(key)
    all.value(key) orCommentTheError s"key='$key'"
    }

    def findHavingOneOf(variants:String*): Result[String] = {
    for (key <- variants) {
    val found = findAllHaving(key).value(key) orCommentTheError s"key='$key'"
    if (found) return found
    }
    Result.error(s"Failed to find unique entry for ${variants mkString ", "}")
    }

private lazy val submapOfIndexes = innerMap.filterKeys(_ matches (sNumberKey + "\\..*"))

    lazy val findAllIndexed:Props = Props(submapOfIndexes)

    lazy val isAnArray:Boolean = {
    !isEmpty && (submapOfIndexes == innerMap || innerMap.keySet.forall(_ matches sNumberKey))
    }

    def findAllHavingNumber(i: Int) = {
    val subMapWithIndexes = innerMap.filterKeys(_ matches (sNumberKey + "\\..*"))
    val atThisLevel = subMapWithIndexes filterKeys ( _ startsWith( numberKey(i)+"."))
    if (atThisLevel.nonEmpty || subMapWithIndexes.nonEmpty) Props(atThisLevel)
    else findAllHaving(numberKey(i))
    }

private def extractCommonKeyValue(key: String): Result[String] = {
    val found: Set[String] = fullKeys map {
    k ⇒ k.split("\\.").dropWhile(_ != key).toList.drop(1).headOption
    } collect {
    case Some(v) ⇒ v
    } toSet

    found.toList match {
    case Nil ⇒ Result.error("Not found")
    case (v:String)::Nil ⇒ Good(v)
    case more  ⇒
    Result.error(s"Too many variants (${more.length})")
    }
    }

private def value(key: String) = {
    val iterable: Iterable[String] = innerMap map (_._2)
    val values:Set[String] = iterable.toSet
    values.size match {
    case 0 ⇒ Result.error("No value found")
    case 1 ⇒ Good(values.head)
    case n ⇒
    val variants = values map (_.replaceAll(" ", ""))
    if (variants.size > 1) {
    extractCommonKeyValue(key)
    //          Result.error(s"Found $n values, had to be just one")
    }
    else Good(values.head)
    }
    }

    def trimPrefixesWhile(p: String⇒Boolean, collectedPrefixes:List[String] = Nil):(Props, String) = {
    if (keySet.size == 1 && p(keySet.head)) {
    val prefix = keySet.head
    val t = dropPrefix
    t.trimPrefixesWhile(p, prefix::collectedPrefixes)
    } else (self, collectedPrefixes.reverse mkString ".")
    }

private def groupForIndexRange(indexRange: Range.Inclusive): Seq[Props] = {
    val result: Seq[Props] = indexRange map (i ⇒ {
    val key = numberKey(i)
    val havingThisNumber = findAllHavingNumber(i)
    val droppedUpToNumber = havingThisNumber.trimPrefixesWhile(key !=)._1
    droppedUpToNumber.dropPrefix
    })
    result
    }

private[scalakittens] lazy val indexRange = {
    val indexes = keySet collect { case NumberKeyPattern(n) ⇒ n.toInt }
    1 to (if (indexes.isEmpty) 0 else indexes.max)
    }

    def extractIndexed: Seq[Props] = {
    if (indexRange.toSet subsetOf indexRange.toSet) {
    groupForIndexRange(indexRange)
    } else this::Nil
    }

    def groupByIndex: Seq[Props] = {
    if (keySet forall isNumberKey) extractIndexed
    else this::Nil
    }

    def extractAllNumberedHaving(keys: String): Seq[Props] = {
    val containingKeys = findAllHaving(keys)
    val found = containingKeys.trimPrefixesWhile(key ⇒ !isNumberKey(key))
    val allIndexed = subtree(found._2)
    allIndexed groupByIndex
    }

    def addNumber(i: Int) = addPrefix(numberKey(i))

    def addPrefix(rawPrefix: String): Props = {
    val prefix = trimKey(rawPrefix)

    if (prefix.trim.isEmpty) this else {
    val newMap = innerMap map (kv ⇒ prefix+"."+kv._1 -> kv._2)
    Props(newMap)
    }
    }

    def reorder(paramsOrder:Int*):Props = if (isEmpty) this else {
    val keyMap = paramsOrder.zipWithIndex.toMap
    val newOrder = 1 to paramsOrder.size map keyMap

    def remap(key: String) = {
    val keys = key split "\\."
    if (keys.length < paramsOrder.length) key
    else newOrder map keys mkString "."
    }

    Props(innerMap map { case(k,v) ⇒ remap(k) → v })
    }

    def addPrefixes(ps: Seq[String]): Props = if (isEmpty) self else ps.foldRight(self)((p, t) ⇒ t.addPrefix(p))

private def checkForEmptyKeys():Unit = {
    if (keysWithEmptyValues.nonEmpty) {
    throw new IllegalArgumentException(s"Bad this: $self; check out keys $keysWithEmptyValues")
    }
    }

    def ++(that:Props): Props = {
    checkForEmptyKeys()
    that.checkForEmptyKeys()

    if (this.isEmpty) that else
    if (that.isEmpty) this else
    Props(self.innerMap ++ that.innerMap)
    }

    def ++(that: Map[String, String]): Props = ++(props(that))

    def endingWith(postfix: String): Props = Props(
    innerMap collect { case kv if kv._1.endsWith("." + postfix) ⇒
    val newLength = kv._1.length - postfix.length - 1
    (kv._1.substring(0, newLength), kv._2)
    })

    def startingWith(prefix: String): Props = Props(
    innerMap collect { case kv if kv._1.startsWith(prefix + ".") ⇒
    (kv._1.substring(prefix.length+1), kv._2)
    })

    // for testing
    def keysWithEmptyValues = fullKeys filter (apply(_).isEmpty)

    def transformKeys(keyTransformer: String⇒String) = props(innerMap)(keyTransformer)

private[scalakittens] def stringAt(key:String, op: Props ⇒ String) = {
    val sub = subtree(key)
    val v = valueOf(key)
    if (!sub.isEmpty) op(sub) else v match {
    case Good(something) ⇒ Strings.powerString(""+something).quote
    case bad ⇒ ""
    }
    }

    def toJsonString: String = {
    Props.DEPTH_COUNTER = Props.DEPTH_COUNTER + 1
    def stringifyAt(k: String) = {
    val pref:String = "-"*Props.DEPTH_COUNTER
    //      println(s"2JS.sA:$pref$k.")
    stringAt(k, pp ⇒ pp.toJsonString)
    }

    if (isAnArray) {
    val strings = indexRange map { i ⇒ stringifyAt(numberKey(i)) }
    strings mkString("[", ", ", "]")
    } else {
    val keys = keySet
    val fks = fullKeys
    val ks0 = fks map (_.split("\\.", 2)(0))
    val stringifiedValues = keys map (k ⇒ s""""$k": ${stringifyAt(k)}""")
    val result = stringifiedValues mkString("{", ", ", "}")
    Props.DEPTH_COUNTER = Props.DEPTH_COUNTER - 1
    result
    }
    }
    }

    trait PropsOps {
    val Id:String⇒String = identity

    def numberKey(i: Int) = s"[[$i]]"

    def replaceAll(map: PropMap): String⇒String = s ⇒ {
    map.keys.find(s matches).fold(s)(key ⇒ key.r.replaceAllIn(s, map(key)))
    }
    def replaceAll(mappings: (String, String)*): String⇒String = replaceAll(mappings.toMap)

    type PropMap = Map[String, String]

    implicit def props(pf: Props): Props = pf

    def props(map: PropMap)(implicit keyTransformer: String⇒String): Props = {
    if (map.isEmpty) Props.empty else {
    def transform(key: String): String = key.split("\\.").map(keyTransformer).mkString(".")
    val transformedData = map.map(kv ⇒ (transform(kv._1), ""+kv._2))
    val newMap = transformedData filter { case (k,v) ⇒ !k.isEmpty && !v.isEmpty}
    val result = Props(newMap)
    if (result.keysWithEmptyValues.nonEmpty) throw new IllegalArgumentException(s"bad map $newMap from $map")
    result
    }
    }

    def props(map: Map[_, _]): Props = props(map map {case (k,v) ⇒ k.toString→v.toString})

    def props(pairs: (String, String)*)(implicit keyTransformer: String⇒String): Props = {
    props(pairs.toMap)(keyTransformer)
    }

    def propsFromTable(table: Iterable[_])(implicit keyTransformer: String⇒String): Props = {
    val raw:PropMap = table.collect {
    case List(x, y)  ⇒ (x, y)
    case Array(x, y) ⇒ (x, y)
    case (x, y)      ⇒ (x, y)
    } .map ({case (k,v) ⇒ ("" + k) → ("" + v)}).
    toMap[String, String]
    props(raw)(keyTransformer)
    }

    def seq2props(seq: List[_])(implicit keyTransformer: String⇒String): Props = {
    val trimmed = seq .map (_.toString.replace(":", "").trim)
    val cleanedUp = ((List[String](), 'Value) /: trimmed) ((p, x) ⇒ {
    p match {
    case (list, 'Value) ⇒ if (x.isEmpty) p else (x::list, 'Key)
    case (list, 'Key)   ⇒ (x::list, 'Value)
    }
    })

    props(cleanedUp._1.reverse.grouped(2) .collect{
    case k::v::Nil ⇒ k.toLowerCase.replaceAll(" ", "") → v
    }.toMap)(keyTransformer)
    }

    // opportunistically extract props from sequence of strings, some of them being empty etc
    def fromStrings(source: Seq[String], separator: String=":"): Props = {
    props(source map (_.split(separator, 2)) filter (_.length == 2) map (kv ⇒ kv(0).trim→kv(1).trim) toMap)
    }

private lazy val PropertyFormat = "([\\w\\.]+) *= *(.*)".r

    def fromSource(source: ⇒Source): Props = {
    var lines = Result.forValue(source.getLines().toList).getOrElse(List(""))

    fromPropLines(lines)
    }

    def fromPropLines(lines: Seq[String]): Props = {
    val QQ = "\\\"(.*)\\\"$".r
    def unquote(s: String) = s match {
    case QQ(s1) ⇒ s1
    case s2     ⇒ s2
    }
    def trim(s: String) = unquote(s.trim)
    val map = lines.
    filter(line ⇒ !line.startsWith("#") && !line.isEmpty).
    collect { case PropertyFormat(key, value) ⇒ key → trim(value) }.
    toMap

    props(map)
    }

    // opportunistically extract props from sequence of strings, some of them being empty etc
    def fromParallelLists(names: List[String], values: List[String], expected: List[String]): Result[Props] = {
    val missing = expected.map(_.toLowerCase).toSet diff names.map(_.toLowerCase).toSet
    val result: Result[Props] = Good(props(names zip values toMap))
    result.filter((p:Props) ⇒ missing.isEmpty, s"Missing column(s) (${missing mkString ","}) in ${names mkString ","}")
    }

    def fromList(source:List[String]) = propsFromTable(source.zipWithIndex map { case (a, i) ⇒ numberKey(i+1) → a})

    def isPrimitive(x: Any) = x match {
    case u: Unit    ⇒ true
    case z: Boolean ⇒ true
    case b: Byte    ⇒ true
    case c: Char    ⇒ true
    case s: Short   ⇒ true
    case i: Int     ⇒ true
    case j: Long    ⇒ true
    case f: Float   ⇒ true
    case d: Double  ⇒ true
    case _          ⇒ false
    }

private[scalakittens] def parsePair(k:Any, v:Any):Result[Props] = v match {
    case s:String            ⇒ Good(props(k.toString → s))
    case x if isPrimitive(x) ⇒ Good(props(k.toString → v.toString))
    case other               ⇒ fromTree(other) map(_ addPrefix k.toString)
    }

    def fromTree(source:Any):Result[Props] = source match {
    case l: List[_]  ⇒ Good(fromList(l map (_.toString)))
    case m: Map[_,_] ⇒
    val pairs: TraversableOnce[Result[Props]] = m map (parsePair _).tupled
    val result: Result[Traversable[Props]] = Result traverse pairs
    result map Props.accumulate
    case bs          ⇒ Result.error(s"Cannot extract properties from $bs")
    }

    }

    object Props extends PropsOps {

    var DEPTH_COUNTER = 0

    val sNumberKey = "\\[\\[(\\d+)\\]\\]"
    val NumberKeyPattern = sNumberKey.r
    lazy val empty = Props(Map.empty)
    val isNumberKey = (s:String) ⇒ s matches sNumberKey

//  def unapply(fp: Props): Option[PropMap] = Some(fp.innerMap)

private val prefixSizeLimits = (1, 70)

private def goodSize(s: String): Boolean = s.length >= prefixSizeLimits._1 && s.length <= prefixSizeLimits._2

private def goodCharacter(s: String): Boolean = !("$0" contains s(0))

private def trimKey(key: String) = {
    val found = key split "\n" find (!_.isEmpty) getOrElse ""
    val trimmed = found split "\\." map (_.trim) filter goodSize filter goodCharacter mkString "."
    trimmed
    }

    def accumulate(pp: TraversableOnce[Props]): Props = (Props.empty /: pp)(_++_)
    def fold(collection: TraversableOnce[Props]) = (empty /: collection)(_++_)

    def foldWithIndex(pss: TraversableOnce[Props]): Props = accumulate(pss.toList.zipWithIndex.map { case (ps, i) ⇒ ps.addNumber(i)})

private def isaLols(x: Any) = x match {
    case l: List[_] ⇒ l.forall({
    case ll: List[_] ⇒ ll.forall(_.isInstanceOf[String])
    case _ ⇒ false
    })
    case _ ⇒ false
    }

    // todo(vlad): civilize it
    def collectProps(source: Any): Result[Seq[Props]] = source match {
    case lols: List[_] if lols.forall(isaLols) ⇒
    val goodLols = lols.asInstanceOf[List[List[List[String]]]]
    val props = goodLols map propsFromTable
    Good(props)

    case x ⇒ Result.error(s"Failed to retrieve props from table: $x in $source")
    }

private def sameKeyBundle(theKeyWeHave: String, suggestedAnyCase: String) = {
    val suggested = suggestedAnyCase.toLowerCase
    val ourKey = theKeyWeHave.toLowerCase

    ourKey == suggested || {
    val keys = ourKey.split("\\.").toSet
    val suggestedKeys = suggested.split("\\.") .map (_.replaceAll("\\?", ".")) .toSet

    keys == suggestedKeys
    }
    }

private def containsKeyBundle(theKeyWeHave: String, suggestedAnyCase: String) = {
    val suggested = suggestedAnyCase.toLowerCase
    val ourKey = theKeyWeHave.toLowerCase

    ourKey == suggested || {
    val keys = ourKey.split("\\.").toSet
    val suggestedKeys = suggested.split("\\.") .map (_.replaceAll("\\?", ".")) .toSet

    suggestedKeys subsetOf keys
    }
    }

private def dropIndexKeysInSequence(keys: Seq[String]) = keys map {
    case NumberKeyPattern(i) ⇒ ""
    case x ⇒ x
    } filter (!_.isEmpty)

private def dropIndexKeys(map: PropMap) = {
    val withSplitKeys = map.map(kv ⇒ (kv._1.split('.'), kv._2))
    val cleanedUpKeys = withSplitKeys.map(kv ⇒ (dropIndexKeysInSequence(kv._1), kv._2))
    val withGoodKeys = cleanedUpKeys.filter(kv ⇒ kv._1.nonEmpty)
    val withNewKeys = withGoodKeys.map(kv ⇒ (kv._1 mkString ".", kv._2))
    withNewKeys
    }

private def mapify(seq: Seq[Any]) = seq.zipWithIndex map {
    case (x, i) ⇒ numberKey(i+1) → x
    } toMap

    def fromMap(source: Map[_, _]): Props = {
    val collection: Iterable[Props] = source map {
    case (k0, v) ⇒
    val k = k0.toString
    v match {
    case null        ⇒ Props.empty
    case m:Map[_, _] ⇒ fromMap(m).addPrefix(k)
    case s:Seq[_]    ⇒ fromMap(mapify(s)).addPrefix(k)
    case x: Any      ⇒ props(k → (""+x))
    }
    }
    accumulate(collection)
    }

    val parse = new RegexParsers {
    override def skipWhitespace = false
    def number = "\\d+".r
    def numbers = "(" ~> repsep(number, ",") <~ ")"
    def text = "\"" ~> "[^\"]+".r <~ "\""
    def mapPair:Parser[(String, String)] = text ~ " → " ~ text ^^ {
    case k ~ _ ~ v ⇒ k → v
    }

    def mapContents:Parser[List[(String, String)]] = (mapPair ~ rep(",\\s*".r ~> mapPair)) ^^ {
    case h ~ t ⇒ h::t
    }

    // TODO: have tests pass with toFormattedString

    def eol: Parser[Any] = """(\r?\n)+""".r

    def mapExp: Parser[PropMap] = "Map\\s*\\(\\s*".r ~> mapContents <~ "\\s*".r <~ rep(eol) <~")" ^^ (_.toMap)

    def propMapExp: Parser[Props] = "fp\\s*\\(\\s*".r ~> mapExp <~ "\\s*".r <~ rep(eol) <~")" ^^ props

    def withDictionary = " with dictionary " ~> mapExp

    def withReorder = " with reordering " ~> numbers ^^ {_ map (_.toInt)}

    def optionally[X, Y](opt: Option[Y], f: Y ⇒ X ⇒ X): (X ⇒ X) = opt map f getOrElse identity[X]

    def propExp = propMapExp ~ (withDictionary ?) ~ (withReorder ?) ^^ {
    case props ~ dictionaryOpt ~ reorderOpt ⇒
    val withDictionary = (dictionaryOpt fold props) (props translate)
    (reorderOpt fold withDictionary) (withDictionary reorder)
    }

    def apply(s0: String) = {
    val noNL = s0.replaceAll("\\n", " ").trim
    parseAll(propExp, noNL) match {
    case Success(result, _) ⇒ Good(result)
    case NoSuccess(x, y) ⇒ Result.error(s"Failed to parse: $x, $y")
    }
    }
    }

private[scalakittens] def looseMatch(suggested: String, existing: String) = {
    val keySequence = existing.split("\\.").toList

    val suggestedSequence = suggested.split("\\.").toList map (_.replaceAll("\\?", "."))

    def match1(sample: String, segment:String): Outcome = Result.forValue(segment matches sample) filter identity

    def findNext(seq: List[String], sample: String) = seq dropWhile (!match1(sample, _))

    val found = (keySequence /: suggestedSequence)((ks, sample) ⇒ findNext(ks, sample))

    found.nonEmpty
    }


    def keyContains(suggestedAnyCase: String)(theKeyWeHave: String) = {
    val suggested = suggestedAnyCase.toLowerCase
    val ourKey = theKeyWeHave.toLowerCase

    ourKey == suggested || {
    val keySequence = ourKey.split("\\.").toList
    val suggestedSequence = suggested.split("\\.").toList map (_.replaceAll("\\?", "."))
    keySequence.length == suggestedSequence.length &&
    (
    try {
    keySequence zip suggestedSequence forall {
    case (key, s) ⇒ key contains s
    }
    } catch {
    case soe:Throwable ⇒
    false
    })
    }
    }
    }


*/