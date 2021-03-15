
NODE_ENV=development
build-css:
	NODE_ENV=$(NODE_ENV) npm run tw

build-js:
	npx shadow-cljs release app

DATE=$(shell date)
gh-pull:
	cd target && \
	rm -rf .git && \
	git init && \
	git remote add origin git@github.com:pavel-popov/2048-cljs.git && \
	git fetch && \
	git reset --hard origin/gh-pages


gh-push:
	cd target && \
	echo "Release as of $(DATE)" > README.txt && \
	git add css *.html *.js README.txt && \
	git commit -m "Release as of $(DATE)" && \
	git push origin master:gh-pages


deploy:  # deploys a production build to Github Pages
	gh-pull
	make build-js
	make build-css NODE_ENV=production

gh-pages:
	git checkout -b build
	git add --force target/*.html
	git add --force target/*.js
	git add --force target/css/*.css
	git commit -m "Release as of $(DATE)"
	git subtree add --prefix gh-pages origin gh-pages --squash
	git subtree push --prefix target origin gh-pages-new
	git checkout master
	git branch -D build
