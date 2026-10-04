const MARKS = {
  yandex: ["#FC3F1D", "Я"],
  google: ["#4285F4", "G"],
  bing: ["#008373", "B"],
  duckduckgo: ["#DE5833", "D"],
  yahoo: ["#6001D2", "Y"],
  mail: ["#005FF9", "M"],
  rambler: ["#315EFB", "R"],
  brave: ["#FB542B", "B"],
  ecosia: ["#008009", "E"],
  startpage: ["#6573FF", "S"],
  qwant: ["#5C97FF", "Q"],
  wikipedia: ["#202124", "W"],
  baidu: ["#2932E1", "B"],
  naver: ["#03C75A", "N"],
  seznam: ["#CC0000", "S"],
  ask: ["#D32011", "A"],
  aol: ["#202124", "A"],
  kagi: ["#1A1A1A", "K"],
  you: ["#202124", "Y"],
  mojeek: ["#1A4F8B", "M"],
  swisscows: ["#E30613", "S"],
  dogpile: ["#E85D04", "D"],
  metager: ["#2E7D32", "M"],
  presearch: ["#1A56DB", "P"]
};

function icon(id) {
  const mark = MARKS[id] || ["#234230", "?"];
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64"><rect width="64" height="64" rx="16" fill="${mark[0]}"/><text x="32" y="43" text-anchor="middle" font-size="32" font-family="Segoe UI,Arial" fill="#fff">${mark[1]}</text></svg>`;
  return "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
}

module.exports = { icon };
